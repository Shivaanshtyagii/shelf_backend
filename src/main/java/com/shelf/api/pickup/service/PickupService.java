package com.shelf.api.pickup.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.common.error.DomainException;
import com.shelf.api.institutions.api.Affiliations;
import com.shelf.api.listings.api.Listing.Scope;
import com.shelf.api.listings.api.ListingAccess;
import com.shelf.api.pickup.domain.PickupRequest.Status;
import com.shelf.api.pickup.domain.PickupRequest;
import com.shelf.api.pickup.repository.PickupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PickupService {
    private final PickupRepository pickups;
    private final Affiliations affiliations;
    private final ListingAccess listings;

    private final Clock clock;
    private final Accounts accounts;

    PickupService(Accounts accounts, PickupRepository pickups, Affiliations affiliations, ListingAccess listings, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
        this.pickups = pickups;
        this.affiliations = affiliations;
        this.listings = listings;
    }

    public PickupRequest create(Principal principal, UUID listingId) {
        var member = accounts.verified(principal);
        var listing = listings.visible(listingId, member);
        if (listing.scope() != Scope.CAMPUS_MARKETPLACE || listing.ownerId().equals(member.id())) {
            throw DomainException.badRequest("Choose a campus item offered by another seller.");
        }

        return pickups.requestUnreservedItem(UUID.randomUUID(), listingId, listing.title(),
                        listing.institutionId(), member.id(), listing.ownerId())
                .orElseThrow(() -> DomainException.conflict("This item is already reserved."));
    }

    public List<PickupRequest> mine(Principal principal) {
        var member = accounts.current(principal).id();
        return affiliations.verifiedInstitution(member)
                .map(college -> pickups.forParticipant(member, college)).orElseGet(List::of);
    }

    @Transactional
    public PickupRequest change(Principal principal, UUID id, Status status, String location, OffsetDateTime time) {
        var member = accounts.verified(principal);
        var college = affiliations.requireInstitution(member.id());
        var request = pickups.lockForParticipant(id, member.id(), college)
                .orElseThrow(() -> DomainException.missing("Pickup request unavailable."));
        var seller = request.sellerId().equals(member.id());
        var allowed = switch (status) {
            case ACCEPTED, DECLINED -> seller && request.status() == Status.REQUESTED;
            case COLLECTED -> seller && request.status() == Status.ACCEPTED;
            case CANCELLED -> request.status() == Status.REQUESTED || request.status() == Status.ACCEPTED;
            case REQUESTED -> false;
        };
        if (!allowed) throw DomainException.conflict("This pickup cannot move to the selected status.");

        if (status == Status.ACCEPTED) {
            listings.visible(request.listingId(), member);
            if (affiliations.verifiedInstitution(request.buyerId()).filter(request.institutionId()::equals).isEmpty()) {
                throw DomainException.conflict("The buyer no longer belongs to this campus.");
            }
            if (location == null || location.isBlank() || time == null || !time.isAfter(OffsetDateTime.now(clock))) {
                throw DomainException.badRequest("Provide a public meeting location and a future time.");
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
