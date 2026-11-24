package com.shelf.api.moderation.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.common.error.DomainException;
import com.shelf.api.listings.api.Listing;
import com.shelf.api.listings.api.ListingAccess;
import com.shelf.api.listings.api.SellerReview;
import com.shelf.api.moderation.domain.ModerationActivity;
import com.shelf.api.moderation.domain.ModerationDecision;
import com.shelf.api.moderation.domain.ModerationQueue;
import com.shelf.api.moderation.repository.ModerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Service
public class ModerationService {
    private final ModerationRepository moderation;
    private final ListingAccess listings;
    private final SellerReview sellers;
    private final Accounts accounts;

    ModerationService(Accounts accounts, ModerationRepository moderation, ListingAccess listings, SellerReview sellers) {
        this.accounts = accounts;
        this.moderation = moderation;
        this.listings = listings;
        this.sellers = sellers;
    }

    public ModerationQueue queue(Principal principal) {
        accounts.moderator(principal);
        var reports = moderation.pendingReports();
        var appeals = moderation.pendingAppeals();
        return new ModerationQueue(listings.queue(), sellers.sellerQueue(), reports, appeals);
    }

    @Transactional
    public void decideListing(UUID id, Principal principal, int revision, Listing.Status outcome, String reason) {
        var moderator = accounts.moderator(principal).id();
        listings.decide(id, revision, outcome);
        moderation.insertDecision("LISTING", id, revision, moderator, outcome.name(), reason.strip());
    }

    @Transactional
    public void takeDown(UUID id, Principal principal, String reason) {
        var moderator = accounts.moderator(principal).id();
        var listing = listings.takeDown(id);
        moderation.insertDecision("LISTING", id, listing.revision(), moderator, "ARCHIVED", reason.strip());
    }

    @Transactional
    public void decideSeller(UUID id, Principal principal, boolean approved, String reason) {
        var moderator = accounts.moderator(principal).id();
        sellers.decideSeller(id, approved);
        moderation.insertDecision("SELLER", id, null, moderator, approved ? "APPROVED" : "REJECTED", reason.strip());
    }

    public List<ModerationDecision> history(UUID listing, Principal principal) {
        var member = accounts.verified(principal);
        if (!member.moderator()) listings.owned(listing, member.id());
        return moderation.listingHistory(listing);
    }

    public void report(UUID listing, Principal principal, String reason) {
        var member = accounts.verified(principal);
        listings.visible(listing, member);
        moderation.insertReport(UUID.randomUUID(), listing, member.id(), reason.strip());
    }

    public void appeal(long decisionId, Principal principal, String reason) {
        var member = accounts.verified(principal).id();
        var entry = moderation.appealableDecision(decisionId)
                .orElseThrow(() -> DomainException.missing("This decision cannot be appealed."));
        listings.owned(entry.targetId(), member);
        moderation.insertAppeal(UUID.randomUUID(), decisionId, member, reason.strip());
    }

    public ModerationActivity activity(Principal principal) {
        var member = accounts.current(principal).id();
        return new ModerationActivity(moderation.reportsBy(member), moderation.appealsBy(member));
    }

    public void reviewReport(UUID id, Principal principal, boolean accepted, String reason) {
        var moderator = accounts.moderator(principal).id();
        if (!moderation.reviewReport(id, moderator, accepted ? "RESOLVED" : "DISMISSED", reason.strip())) {
            throw DomainException.conflict("This report is no longer pending.");
        }
    }

    public void reviewAppeal(UUID id, Principal principal, boolean accepted, String reason) {
        var moderator = accounts.moderator(principal).id();
        if (!moderation.reviewAppeal(id, moderator, accepted ? "UPHELD" : "DISMISSED", reason.strip())) {
            throw DomainException.conflict("This appeal is no longer pending.");
        }
    }
}
