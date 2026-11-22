package com.shelf.pickup.internal.service;

import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import com.shelf.institutions.Affiliations;
import com.shelf.listings.Listing.Scope;
import com.shelf.listings.ListingAccess;
import com.shelf.pickup.internal.model.PickupRequest;
import com.shelf.pickup.internal.model.PickupRequest.Status;
import com.shelf.pickup.internal.persistence.PickupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PickupService {
    private final PickupRepository pickups;
    private final Affiliations affiliations;
    private final ListingAccess listings;

    PickupService(PickupRepository pickups, Affiliations affiliations, ListingAccess listings) {
        this.pickups = pickups;
        this.affiliations = affiliations;
        this.listings = listings;
    }

    public PickupRequest create(Member member, UUID listingId) {
        var listing = listings.visible(listingId, member);
        if (listing.scope() != Scope.CAMPUS_MARKETPLACE || listing.ownerId().equals(member.id())) {
            throw ApiFault.badRequest("Choose a campus item offered by another seller.");
        }

        return pickups.requestUnreservedItem(UUID.randomUUID(), listingId, listing.title(),
                        listing.institutionId(), member.id(), listing.ownerId())
                .orElseThrow(() -> ApiFault.conflict("This item is already reserved."));
    }

    public List<PickupRequest> mine(UUID member) {
        return affiliations.verifiedInstitution(member)
                .map(college -> pickups.forParticipant(member, college)).orElseGet(List::of);
    }

    @Transactional
    public PickupRequest change(Member member, UUID id, Status status, String location, OffsetDateTime time) {
        var college = affiliations.requireInstitution(member.id());
        var request = pickups.lockForParticipant(id, member.id(), college)
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
        return pickups.changeStatus(id, status, meetingLocation, meetingTime);
    }
}
