package com.shelf.moderation.internal.service;

import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import com.shelf.listings.Listing;
import com.shelf.listings.ListingAccess;
import com.shelf.listings.SellerReview;
import com.shelf.moderation.internal.model.ModerationActivity;
import com.shelf.moderation.internal.model.ModerationDecision;
import com.shelf.moderation.internal.model.ModerationQueue;
import com.shelf.moderation.internal.persistence.ModerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ModerationService {
    private final ModerationRepository moderation;
    private final ListingAccess listings;
    private final SellerReview sellers;

    ModerationService(ModerationRepository moderation, ListingAccess listings, SellerReview sellers) {
        this.moderation = moderation;
        this.listings = listings;
        this.sellers = sellers;
    }

    public ModerationQueue queue() {
        var reports = moderation.pendingReports();
        var appeals = moderation.pendingAppeals();
        return new ModerationQueue(listings.queue(), sellers.sellerQueue(), reports, appeals);
    }

    @Transactional
    public void decideListing(UUID id, UUID moderator, int revision, Listing.Status outcome, String reason) {
        listings.decide(id, revision, outcome);
        moderation.insertDecision("LISTING", id, revision, moderator, outcome.name(), reason.strip());
    }

    @Transactional
    public void takeDown(UUID id, UUID moderator, String reason) {
        var listing = listings.takeDown(id);
        moderation.insertDecision("LISTING", id, listing.revision(), moderator, "ARCHIVED", reason.strip());
    }

    @Transactional
    public void decideSeller(UUID id, UUID moderator, boolean approved, String reason) {
        sellers.decideSeller(id, approved);
        moderation.insertDecision("SELLER", id, null, moderator, approved ? "APPROVED" : "REJECTED", reason.strip());
    }

    public List<ModerationDecision> history(UUID listing, Member member) {
        if (!member.moderator()) listings.owned(listing, member.id());
        return moderation.listingHistory(listing);
    }

    public void report(UUID listing, Member member, String reason) {
        listings.visible(listing, member);
        moderation.insertReport(UUID.randomUUID(), listing, member.id(), reason.strip());
    }

    public void appeal(long decisionId, UUID member, String reason) {
        var entry = moderation.appealableDecision(decisionId)
                .orElseThrow(() -> ApiFault.missing("This decision cannot be appealed."));
        listings.owned(entry.targetId(), member);
        moderation.insertAppeal(UUID.randomUUID(), decisionId, member, reason.strip());
    }

    public ModerationActivity activity(UUID member) {
        return new ModerationActivity(moderation.reportsBy(member), moderation.appealsBy(member));
    }

    public void reviewReport(UUID id, UUID moderator, boolean accepted, String reason) {
        if (!moderation.reviewReport(id, moderator, accepted ? "RESOLVED" : "DISMISSED", reason.strip())) {
            throw ApiFault.conflict("This report is no longer pending.");
        }
    }

    public void reviewAppeal(UUID id, UUID moderator, boolean accepted, String reason) {
        if (!moderation.reviewAppeal(id, moderator, accepted ? "UPHELD" : "DISMISSED", reason.strip())) {
            throw ApiFault.conflict("This appeal is no longer pending.");
        }
    }
}
