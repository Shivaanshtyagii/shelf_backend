package com.shelf.listings.internal;

import com.shelf.accounts.Member;
import com.shelf.assets.Assets;
import com.shelf.common.ApiFault;
import com.shelf.institutions.Affiliations;
import com.shelf.listings.Listing;
import com.shelf.listings.ListingAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.shelf.listings.Listing.Scope;
import static com.shelf.listings.Listing.Status;

@Service
class Marketplace implements ListingAccess {
    private final ListingRepository listings;
    private final Affiliations affiliations;
    private final Assets assets;

    Marketplace(ListingRepository listings, Affiliations affiliations, Assets assets) {
        this.listings = listings;
        this.affiliations = affiliations;
        this.assets = assets;
    }

    Page discover(Scope scope, Member member, ListingRepository.Search query) {
        var institution = scope == Scope.EXPLORE ? null : affiliations.requireInstitution(member.id());
        var results = listings.discover(scope, institution, query);
        return new Page(results.stream().limit(query.size()).map(Card::from).toList(), query.page(),
                results.size() > query.size());
    }

    List<Listing> mine(UUID owner) { return listings.mine(owner); }
    String sellerStatus(UUID owner) { return listings.sellerStatus(owner); }
    void applyToSell(UUID owner) { listings.applyToSell(owner); }

    @Transactional
    public Listing save(UUID owner, UUID id, Draft input) {
        var previous = id == null ? null : ownerListing(id, owner, true);
        if (previous != null && previous.status() != Status.DRAFT && previous.status() != Status.REJECTED) {
            throw ApiFault.conflict("Only draft and rejected listings can be edited.");
        }
        if (previous != null && previous.revision() != input.revision()) {
            throw ApiFault.conflict("This draft has changed. Reload and try again.");
        }
        var college = input.scope() == Scope.EXPLORE ? null : affiliations.requireInstitution(owner);
        if (input.scope() != Scope.CAMPUS_MARKETPLACE && input.priceMinor() != 0) {
            throw ApiFault.badRequest("Academic resources use free access during the pilot.");
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
    public Listing submit(UUID id, UUID owner, int revision) {
        var listing = ownerListing(id, owner, true);
        if (listing.status() != Status.DRAFT || listing.revision() != revision) {
            throw ApiFault.conflict("Save the latest draft before submitting.");
        }
        if (!"APPROVED".equals(listings.sellerStatus(owner))) {
            throw ApiFault.forbidden("Seller onboarding must be approved before submitting.");
        }
        requireAffiliation(listing);
        assets.requireClean(listing.assetId(), owner, kind(listing.scope()));
        return listings.transition(id, Status.PENDING);
    }

    @Transactional
    public Listing archive(UUID id, UUID owner) {
        var listing = ownerListing(id, owner, true);
        if (listing.status() == Status.ARCHIVED) return listing;
        var archived = listings.transition(id, Status.ARCHIVED);
        assets.release(listing.assetId(), owner);
        return archived;
    }

    @Override
    public Listing owned(UUID id, UUID owner) { return ownerListing(id, owner, false); }

    @Override
    public Listing visible(UUID id, Member member) {
        var listing = listings.find(id, false).filter(row -> row.status() == Status.APPROVED)
                .orElseThrow(() -> ApiFault.missing("Listing unavailable."));
        if (listing.scope() != Scope.EXPLORE && (!member.verified()
                || !affiliations.verifiedInstitution(member.id()).filter(listing.institutionId()::equals).isPresent())) {
            throw ApiFault.missing("Listing unavailable.");
        }
        return listing;
    }

    URI attachment(UUID id, Member member) {
        var listing = listings.find(id, false).orElseThrow(() -> ApiFault.missing("Listing unavailable."));
        var reviewing = member.moderator() && listing.status() == Status.PENDING;
        if (!listing.ownerId().equals(member.id()) && !reviewing) listing = visible(id, member);
        if (listing.status() == Status.ARCHIVED || listing.assetId() == null) throw ApiFault.missing("Attachment unavailable.");
        return assets.download(listing.assetId());
    }

    @Override
    public List<Listing> queue() { return listings.queue(); }

    @Override
    @Transactional
    public Listing decide(UUID id, int revision, Status result) {
        var listing = listings.find(id, true).orElseThrow(() -> ApiFault.missing("Listing unavailable."));
        if (listing.revision() != revision) throw ApiFault.conflict("The listing revision has changed.");
        return applyDecision(listing, result);
    }

    @Override
    @Transactional
    public Listing takeDown(UUID id) {
        var listing = listings.find(id, true).orElseThrow(() -> ApiFault.missing("Listing unavailable."));
        return applyDecision(listing, Status.ARCHIVED);
    }

    private Listing applyDecision(Listing listing, Status result) {
        switch (result) {
            case APPROVED, REJECTED -> {
                if (listing.status() != Status.PENDING) throw ApiFault.conflict("The listing is no longer under review.");
                if (result == Status.APPROVED) {
                    requireAffiliation(listing);
                    assets.requireClean(listing.assetId(), listing.ownerId(), kind(listing.scope()));
                }
            }
            case ARCHIVED -> {
                if (listing.status() != Status.APPROVED) throw ApiFault.conflict("Only published listings can be taken down.");
            }
            default -> throw ApiFault.badRequest("Choose a valid review outcome.");
        }
        var reviewed = listings.transition(listing.id(), result);
        if (result == Status.ARCHIVED) assets.release(listing.assetId(), listing.ownerId());
        return reviewed;
    }

    @Override
    public List<UUID> sellerQueue() { return listings.sellerQueue(); }

    @Override
    public void decideSeller(UUID memberId, boolean approved) {
        if (!listings.decideSeller(memberId, approved)) throw ApiFault.conflict("This seller request is no longer pending.");
    }

    private Listing ownerListing(UUID id, UUID owner, boolean lock) {
        return listings.find(id, lock).filter(row -> row.ownerId().equals(owner))
                .orElseThrow(() -> ApiFault.missing("Listing unavailable."));
    }

    private void requireAffiliation(Listing listing) {
        if (listing.institutionId() != null && !listing.institutionId().equals(affiliations.requireInstitution(listing.ownerId()))) {
            throw ApiFault.forbidden("The listing belongs to a different campus.");
        }
    }

    private Assets.Kind kind(Scope scope) {
        return scope == Scope.CAMPUS_MARKETPLACE ? Assets.Kind.PHOTO : Assets.Kind.PDF;
    }

    record Draft(Scope scope, String title, String description, String subject, int priceMinor, UUID assetId, int revision) {}
    record Page(List<Card> items, int page, boolean hasNext) {}
    record Card(UUID id, Scope scope, String title, String description, String subject, int priceMinor, OffsetDateTime publishedAt) {
        static Card from(Listing row) {
            return new Card(row.id(), row.scope(), row.title(), row.description(), row.subject(), row.priceMinor(), row.publishedAt());
        }
    }
}
