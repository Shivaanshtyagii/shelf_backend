package com.shelf.moderation.internal.persistence;

import com.shelf.moderation.internal.model.ModerationAppeal;
import com.shelf.moderation.internal.model.ModerationDecision;
import com.shelf.moderation.internal.model.ModerationReport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ModerationRepository {
    private final JdbcClient db;

    ModerationRepository(JdbcClient db) { this.db = db; }

    public List<ModerationReport> pendingReports() {
        return db.sql("""
                select id, listing_id, reporter_id, reason, status, response, created_at
                from moderation.report where status = 'PENDING' order by created_at limit 100
                """).query(ModerationReport.class).list();
    }

    public List<ModerationAppeal> pendingAppeals() {
        return db.sql("""
                select id, decision_id, member_id, reason, status, response, created_at
                from moderation.appeal where status = 'PENDING' order by created_at limit 100
                """).query(ModerationAppeal.class).list();
    }

    public List<ModerationDecision> listingHistory(UUID listing) {
        return db.sql("""
                select id, target_id, revision, decision as outcome, reason, created_at
                from moderation.decision where target_type = 'LISTING' and target_id = :id order by id desc limit 100
                """).param("id", listing).query(ModerationDecision.class).list();
    }

    public void insertReport(UUID id, UUID listing, UUID member, String reason) {
        db.sql("""
                insert into moderation.report(id, listing_id, reporter_id, reason)
                values (:id, :listing, :member, :reason)
                """).param("id", id).param("listing", listing).param("member", member).param("reason", reason).update();
    }

    public Optional<ModerationDecision> appealableDecision(long id) {
        return db.sql("""
                select id, target_id, revision, decision as outcome, reason, created_at from moderation.decision
                where id = :id and target_type = 'LISTING' and decision in ('REJECTED', 'ARCHIVED')
                """).param("id", id).query(ModerationDecision.class).optional();
    }

    public void insertAppeal(UUID id, long decision, UUID member, String reason) {
        db.sql("""
                insert into moderation.appeal(id, decision_id, member_id, reason)
                values (:id, :decision, :member, :reason)
                """).param("id", id).param("decision", decision).param("member", member).param("reason", reason).update();
    }

    public List<ModerationReport> reportsBy(UUID member) {
        return db.sql("""
                select id, listing_id, reporter_id, reason, status, response, created_at from moderation.report
                where reporter_id = :member order by created_at desc limit 100
                """).param("member", member).query(ModerationReport.class).list();
    }

    public List<ModerationAppeal> appealsBy(UUID member) {
        return db.sql("""
                select id, decision_id, member_id, reason, status, response, created_at from moderation.appeal
                where member_id = :member order by created_at desc limit 100
                """).param("member", member).query(ModerationAppeal.class).list();
    }

    public boolean reviewReport(UUID id, UUID moderator, String status, String reason) {
        return db.sql("""
                update moderation.report set status = :status, response = :reason, reviewed_by = :moderator
                where id = :id and status = 'PENDING'
                """).param("status", status).param("reason", reason)
                .param("moderator", moderator).param("id", id).update() == 1;
    }

    public boolean reviewAppeal(UUID id, UUID moderator, String status, String reason) {
        return db.sql("""
                update moderation.appeal set status = :status, response = :reason, reviewed_by = :moderator
                where id = :id and status = 'PENDING'
                """).param("status", status).param("reason", reason)
                .param("moderator", moderator).param("id", id).update() == 1;
    }

    public void insertDecision(String targetType, UUID id, Integer revision, UUID moderator, String outcome, String reason) {
        db.sql("""
                insert into moderation.decision(target_type, target_id, revision, moderator_id, decision, reason)
                values (:type, :id, :revision, :moderator, :status, :reason)
                """).param("type", targetType).param("id", id).param("revision", revision)
                .param("moderator", moderator).param("status", outcome).param("reason", reason).update();
    }
}
