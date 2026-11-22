package com.shelf.moderation.internal;

import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import com.shelf.listings.Listing;
import com.shelf.listings.ListingAccess;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
class ModerationService {
    private final JdbcClient db;
    private final ListingAccess listings;

    ModerationService(JdbcClient db, ListingAccess listings) {
        this.db = db;
        this.listings = listings;
    }

    Queue queue() {
        var reports = db.sql("""
                select id, listing_id, reporter_id, reason, status, response, created_at
                from moderation.report where status = 'PENDING' order by created_at limit 100
                """).query(Report.class).list();
        var appeals = db.sql("""
                select id, decision_id, member_id, reason, status, response, created_at
                from moderation.appeal where status = 'PENDING' order by created_at limit 100
                """).query(Appeal.class).list();
        return new Queue(listings.queue(), listings.sellerQueue(), reports, appeals);
    }

    @Transactional
    public void decideListing(UUID id, UUID moderator, int revision, Listing.Status outcome, String reason) {
        listings.decide(id, revision, outcome);
        recordDecision("LISTING", id, revision, moderator, outcome.name(), reason);
    }

    @Transactional
    public void takeDown(UUID id, UUID moderator, String reason) {
        var listing = listings.takeDown(id);
        recordDecision("LISTING", id, listing.revision(), moderator, "ARCHIVED", reason);
    }

    @Transactional
    public void decideSeller(UUID id, UUID moderator, boolean approved, String reason) {
        listings.decideSeller(id, approved);
        recordDecision("SELLER", id, null, moderator, approved ? "APPROVED" : "REJECTED", reason);
    }

    List<Decision> history(UUID listing, Member member) {
        if (!member.moderator()) listings.owned(listing, member.id());
        return db.sql("""
                select id, target_id, revision, decision as outcome, reason, created_at
                from moderation.decision where target_type = 'LISTING' and target_id = :id order by id desc limit 100
                """).param("id", listing).query(Decision.class).list();
    }

    void report(UUID listing, Member member, String reason) {
        listings.visible(listing, member);
        db.sql("""
                insert into moderation.report(id, listing_id, reporter_id, reason)
                values (:id, :listing, :member, :reason)
                """).param("id", UUID.randomUUID()).param("listing", listing).param("member", member.id())
                .param("reason", reason.strip()).update();
    }

    void appeal(long decisionId, UUID member, String reason) {
        var entry = db.sql("""
                select id, target_id, revision, decision as outcome, reason, created_at from moderation.decision
                where id = :id and target_type = 'LISTING' and decision in ('REJECTED', 'ARCHIVED')
                """).param("id", decisionId).query(Decision.class).optional()
                .orElseThrow(() -> ApiFault.missing("This decision cannot be appealed."));
        listings.owned(entry.targetId(), member);
        db.sql("""
                insert into moderation.appeal(id, decision_id, member_id, reason)
                values (:id, :decision, :member, :reason)
                """).param("id", UUID.randomUUID()).param("decision", decisionId).param("member", member)
                .param("reason", reason.strip()).update();
    }

    Activity activity(UUID member) {
        var reports = db.sql("""
                select id, listing_id, reporter_id, reason, status, response, created_at from moderation.report
                where reporter_id = :member order by created_at desc limit 100
                """).param("member", member).query(Report.class).list();
        var appeals = db.sql("""
                select id, decision_id, member_id, reason, status, response, created_at from moderation.appeal
                where member_id = :member order by created_at desc limit 100
                """).param("member", member).query(Appeal.class).list();
        return new Activity(reports, appeals);
    }

    void reviewReport(UUID id, UUID moderator, boolean accepted, String reason) {
        var changed = db.sql("""
                update moderation.report set status = :status, response = :reason, reviewed_by = :moderator
                where id = :id and status = 'PENDING'
                """).param("status", accepted ? "RESOLVED" : "DISMISSED").param("reason", reason.strip())
                .param("moderator", moderator).param("id", id).update();
        if (changed != 1) throw ApiFault.conflict("This report is no longer pending.");
    }

    void reviewAppeal(UUID id, UUID moderator, boolean accepted, String reason) {
        var changed = db.sql("""
                update moderation.appeal set status = :status, response = :reason, reviewed_by = :moderator
                where id = :id and status = 'PENDING'
                """).param("status", accepted ? "UPHELD" : "DISMISSED").param("reason", reason.strip())
                .param("moderator", moderator).param("id", id).update();
        if (changed != 1) throw ApiFault.conflict("This appeal is no longer pending.");
    }

    private void recordDecision(String type, UUID id, Integer revision, UUID moderator, String outcome, String reason) {
        db.sql("""
                insert into moderation.decision(target_type, target_id, revision, moderator_id, decision, reason)
                values (:type, :id, :revision, :moderator, :status, :reason)
                """).param("type", type).param("id", id).param("revision", revision)
                .param("moderator", moderator).param("status", outcome).param("reason", reason.strip()).update();
    }

    record Queue(List<Listing> listings, List<UUID> sellers, List<Report> reports, List<Appeal> appeals) {}
    record Activity(List<Report> reports, List<Appeal> appeals) {}
    record Decision(long id, UUID targetId, Integer revision, String outcome, String reason, OffsetDateTime createdAt) {}
    record Report(UUID id, UUID listingId, UUID reporterId, String reason, String status, String response, OffsetDateTime createdAt) {}
    record Appeal(UUID id, long decisionId, UUID memberId, String reason, String status, String response, OffsetDateTime createdAt) {}
}
