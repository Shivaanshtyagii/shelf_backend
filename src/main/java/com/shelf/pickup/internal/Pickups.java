package com.shelf.pickup.internal;

import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import com.shelf.institutions.Affiliations;
import com.shelf.listings.Listing.Scope;
import com.shelf.listings.ListingAccess;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
class Pickups {
    private static final String DETAILS = """
            id, listing_id, listing_title, institution_id, buyer_id, seller_id, status, meeting_location, meeting_time, created_at
            """;
    private final JdbcClient db;
    private final Affiliations affiliations;
    private final ListingAccess listings;

    Pickups(JdbcClient db, Affiliations affiliations, ListingAccess listings) {
        this.db = db;
        this.affiliations = affiliations;
        this.listings = listings;
    }

    Request create(Member member, UUID listingId) {
        var listing = listings.visible(listingId, member);
        if (listing.scope() != Scope.CAMPUS_MARKETPLACE || listing.ownerId().equals(member.id())) {
            throw ApiFault.badRequest("Choose a campus item offered by another seller.");
        }

        var id = UUID.randomUUID();
        return db.sql("""
                insert into pickup.request(id, listing_id, listing_title, institution_id, buyer_id, seller_id, status)
                select :id, :listing, :title, :college, :buyer, :seller, 'REQUESTED'
                where not exists (
                    select 1 from pickup.request where listing_id = :listing and status in ('ACCEPTED', 'COLLECTED')
                )
                returning
                """ + DETAILS).param("id", id).param("listing", listingId).param("title", listing.title()).param("college", listing.institutionId())
                .param("buyer", member.id()).param("seller", listing.ownerId()).query(Request.class).optional()
                .orElseThrow(() -> ApiFault.conflict("This item is already reserved."));
    }

    List<Request> mine(UUID member) {
        var college = affiliations.verifiedInstitution(member);
        if (college.isEmpty()) return List.of();
        return db.sql("select " + DETAILS + """
                from pickup.request where (buyer_id = :member or seller_id = :member) and institution_id = :campus
                order by created_at desc limit 100
                """).param("member", member).param("campus", college.get()).query(Request.class).list();
    }

    @Transactional
    public Request change(Member member, UUID id, Status status, String location, OffsetDateTime time) {
        var request = db.sql("select " + DETAILS + """
                from pickup.request where id = :id and institution_id = :campus
                    and (buyer_id = :member or seller_id = :member) for update
                """).param("id", id).param("campus", affiliations.requireInstitution(member.id()))
                .param("member", member.id()).query(Request.class).optional()
                .orElseThrow(() -> ApiFault.missing("Pickup request unavailable."));
        var seller = request.sellerId().equals(member.id());
        var allowed = switch (status) {
            case ACCEPTED, DECLINED -> seller && request.status() == Status.REQUESTED;
            case COLLECTED -> seller && request.status() == Status.ACCEPTED;
            case CANCELLED -> request.status() == Status.REQUESTED || request.status() == Status.ACCEPTED;
            case REQUESTED -> false;
        };
        if (!allowed) throw ApiFault.conflict("This pickup cannot move to the selected status.");

        if (status == Status.ACCEPTED) {
            listings.visible(request.listingId(), member);
            if (affiliations.verifiedInstitution(request.buyerId()).filter(request.institutionId()::equals).isEmpty()) {
                throw ApiFault.conflict("The buyer no longer belongs to this campus.");
            }
            if (location == null || location.isBlank() || time == null || !time.isAfter(OffsetDateTime.now())) {
                throw ApiFault.badRequest("Provide a public meeting location and a future time.");
            }
        }
        var meetingLocation = switch (status) {
            case ACCEPTED -> location.strip();
            case CANCELLED, DECLINED -> null;
            default -> request.meetingLocation();
        };
        var meetingTime = switch (status) {
            case ACCEPTED -> time;
            case CANCELLED, DECLINED -> null;
            default -> request.meetingTime();
        };
        return db.sql("""
                update pickup.request set status = :status, meeting_location = :location, meeting_time = :time
                where id = :id returning
                """ + DETAILS).param("status", status.name()).param("location", meetingLocation)
                .param("time", meetingTime).param("id", id).query(Request.class).single();
    }

    enum Status { REQUESTED, ACCEPTED, DECLINED, CANCELLED, COLLECTED }
    record Request(UUID id, UUID listingId, String listingTitle, UUID institutionId, UUID buyerId, UUID sellerId, Status status,
                   String meetingLocation, OffsetDateTime meetingTime, OffsetDateTime createdAt) {}
}
