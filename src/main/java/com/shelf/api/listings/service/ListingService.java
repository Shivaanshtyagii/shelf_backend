package com.shelf.api.listings.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.accounts.api.Member;
import com.shelf.api.assets.api.Assets;
import com.shelf.api.common.error.DomainException;
import com.shelf.api.institutions.api.Affiliations;
import com.shelf.api.listings.api.Listing;
import com.shelf.api.listings.api.ListingAccess;
import com.shelf.api.listings.api.ListingPageResponse;
import com.shelf.api.listings.domain.ListingDraft;
import com.shelf.api.listings.domain.ListingSearch;
import com.shelf.api.listings.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.shelf.api.listings.api.Listing.Scope;
import static com.shelf.api.listings.api.Listing.Status;

@Service
public class ListingService implements ListingAccess {
    private final ListingRepository listings;
    private final Affiliations affiliations;
    private final Assets assets;
    private final SellerService sellers;
    private final Accounts accounts;

    ListingService(Accounts accounts, ListingRepository listings, Affiliations affiliations, Assets assets, SellerService sellers) {
        this.accounts = accounts;
        this.listings = listings;
        this.affiliations = affiliations;
        this.assets = assets;
        this.sellers = sellers;
    }

    public ListingPageResponse campus(Scope scope, Principal principal, ListingSearch query) {
        if (scope == Scope.EXPLORE) throw DomainException.badRequest("Choose a campus category.");
        return discover(scope, accounts.verified(principal), query);
    }

    public ListingPageResponse explore(ListingSearch query) {
        return discover(Scope.EXPLORE, null, query);
    }

    private ListingPageResponse discover(Scope scope, Member member, ListingSearch query) {
        var institution = scope == Scope.EXPLORE ? null : affiliations.requireInstitution(member.id());
        var results = listings.discover(scope, institution, query);
        return new ListingPageResponse(results.stream().limit(query.size()).map(ListingPageResponse.Card::from).toList(), query.page(),
                results.size() > query.size());
    }

    public List<Listing> mine(Principal principal) {
        var owner = accounts.current(principal).id();
        return listings.mine(owner);
    }

    @Transactional
    public Listing save(Principal principal, UUID id, ListingDraft input) {
        var owner = accounts.verified(principal).id();
        var previous = id == null ? null : ownerListing(id, owner, true);
        if (previous != null && previous.status() != Status.DRAFT && previous.status() != Status.REJECTED) {
            throw DomainException.conflict("Only draft and rejected listings can be edited.");
        }
        if (previous != null && previous.revision() != input.revision()) {
            throw DomainException.conflict("This draft has changed. Reload and try again.");
        }
        var college = input.scope() == Scope.EXPLORE ? null : affiliations.requireInstitution(owner);
        if (input.scope() != Scope.CAMPUS_MARKETPLACE && input.priceMinor() != 0) {
            throw DomainException.badRequest("Academic resources use free access during the pilot.");
        }

        var previousAsset = previous == null ? null : previous.assetId();
        if (input.assetId() != null && Objects.equals(previousAsset, input.assetId())) {
            assets.requireClean(input.assetId(), owner, kind(input.scope()));
        } else {
            if (input.assetId() != null) assets.claim(input.assetId(), owner, kind(input.scope()));
            assets.release(previousAsset, owner);
        }
        var listing = new Listing(id == null ? UUID.randomUUID() : id, owner, input.scope(), college,
                input.title().strip(), input.description().strip(), input.subject().strip(), input.priceMinor(),
                input.assetId(), Status.DRAFT, 1, null);
        if (previous == null) listings.insert(listing);
        else listings.replace(listing);
        return ownerListing(listing.id(), owner, false);
    }

    @Transactional
    public Listing submit(UUID id, Principal principal, int revision) {
        var owner = accounts.verified(principal).id();
        var listing = ownerListing(id, owner, true);
        if (listing.status() != Status.DRAFT || listing.revision() != revision) {
            throw DomainException.conflict("Save the latest draft before submitting.");
        }
        sellers.requireApproved(owner);
        requireAffiliation(listing);
        assets.requireClean(listing.assetId(), owner, kind(listing.scope()));
        return listings.transition(id, Status.PENDING);
    }

    @Transactional
    public Listing archive(UUID id, Principal principal) {
        var owner = accounts.verified(principal).id();
        var listing = ownerListing(id, owner, true);
        if (listing.status() == Status.ARCHIVED) return listing;
        var archived = listings.transition(id, Status.ARCHIVED);
        assets.release(listing.assetId(), owner);
        return archived;
    }

    public Listing myListing(UUID id, Principal principal) {
        return owned(id, accounts.current(principal).id());
    }

    @Override
    public Listing owned(UUID id, UUID owner) { return ownerListing(id, owner, false); }

    @Override
    public Listing visible(UUID id, Member member) {
        var listing = listings.find(id, false).filter(row -> row.status() == Status.APPROVED)
                .orElseThrow(() -> DomainException.missing("Listing unavailable."));
        if (listing.scope() != Scope.EXPLORE && (!member.verified()
                || !affiliations.verifiedInstitution(member.id()).filter(listing.institutionId()::equals).isPresent())) {
            throw DomainException.missing("Listing unavailable.");
        }
        return listing;
    }

    public URI attachment(UUID id, Principal principal) {
        var member = accounts.verified(principal);
        var listing = listings.find(id, false).orElseThrow(() -> DomainException.missing("Listing unavailable."));
        var reviewing = member.moderator() && listing.status() == Status.PENDING;
        if (!listing.ownerId().equals(member.id()) && !reviewing) listing = visible(id, member);
        if (listing.status() == Status.ARCHIVED || listing.assetId() == null) throw DomainException.missing("Attachment unavailable.");
        return assets.download(listing.assetId());
    }

    @Override
    public List<Listing> queue() { return listings.queue(); }

    @Override
    @Transactional
    public Listing decide(UUID id, int revision, Status result) {
        var listing = listings.find(id, true).orElseThrow(() -> DomainException.missing("Listing unavailable."));
        if (listing.revision() != revision) throw DomainException.conflict("The listing revision has changed.");
        return applyDecision(listing, result);
    }

    @Override
    @Transactional
    public Listing takeDown(UUID id) {
        var listing = listings.find(id, true).orElseThrow(() -> DomainException.missing("Listing unavailable."));
        return applyDecision(listing, Status.ARCHIVED);
    }

    private Listing applyDecision(Listing listing, Status result) {
        switch (result) {
            case APPROVED, REJECTED -> {
                if (listing.status() != Status.PENDING) throw DomainException.conflict("The listing is no longer under review.");
                if (result == Status.APPROVED) {
                    requireAffiliation(listing);
                    assets.requireClean(listing.assetId(), listing.ownerId(), kind(listing.scope()));
                }
            }
            case ARCHIVED -> {
                if (listing.status() != Status.APPROVED) throw DomainException.conflict("Only published listings can be taken down.");
            }
            default -> throw DomainException.badRequest("Choose a valid review outcome.");
        }
        var reviewed = listings.transition(listing.id(), result);
        if (result == Status.ARCHIVED) assets.release(listing.assetId(), listing.ownerId());
        return reviewed;
    }

    private Listing ownerListing(UUID id, UUID owner, boolean lock) {
        return listings.find(id, lock).filter(row -> row.ownerId().equals(owner))
                .orElseThrow(() -> DomainException.missing("Listing unavailable."));
    }

    private void requireAffiliation(Listing listing) {
        if (listing.institutionId() != null && !listing.institutionId().equals(affiliations.requireInstitution(listing.ownerId()))) {
            throw DomainException.forbidden("The listing belongs to a different campus.");
        }
    }

    private Assets.Kind kind(Scope scope) {
        return scope == Scope.CAMPUS_MARKETPLACE ? Assets.Kind.PHOTO : Assets.Kind.PDF;
    }
}
