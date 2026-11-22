package com.shelf.institutions.internal;

import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import com.shelf.institutions.Affiliations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class Directory implements Affiliations {
    private final JdbcClient db;

    Directory(JdbcClient db) { this.db = db; }

    @Override
    public Optional<UUID> verifiedInstitution(UUID memberId) {
        return db.sql("select institution_id from institutions.affiliation where member_id = :member")
                .param("member", memberId).query(UUID.class).optional();
    }

    @Override
    public UUID requireInstitution(UUID memberId) {
        return verifiedInstitution(memberId)
                .orElseThrow(() -> ApiFault.forbidden("Verify your college affiliation to enter this campus."));
    }

    List<Institution> search(String search) {
        return db.sql("""
                select id, name from institutions.institution
                where position(lower(:search) in lower(name)) > 0 or :search = ''
                order by name, id limit 50
                """).param("search", search.strip()).query(Institution.class).list();
    }

    List<Course> courses(UUID institution) {
        return db.sql("select id, code, name from institutions.course where institution_id = :id order by code limit 100")
                .param("id", institution).query(Course.class).list();
    }

    @Transactional
    public void confirmDomain(Member member, UUID id) {
        var domain = db.sql("select email_domain from institutions.institution where id = :id")
                .param("id", id).query(String.class).optional();
        var emailDomain = member.email().substring(member.email().lastIndexOf('@') + 1);
        if (domain.isEmpty() || !domain.get().equalsIgnoreCase(emailDomain)) {
            throw ApiFault.forbidden("The verified account email does not match this college. Submit a verification request.");
        }
        affiliate(member.id(), id);
    }

    UUID request(UUID member, Kind kind, UUID institution, String name, String detail) {
        if (kind != Kind.INSTITUTION && institution == null || kind != Kind.AFFILIATION && (name == null || name.isBlank())) {
            throw ApiFault.badRequest("Provide the college and request details.");
        }
        var id = UUID.randomUUID();
        db.sql("""
                insert into institutions.request(id, member_id, kind, institution_id, name, detail)
                values (:id, :member, :kind, :institution, :name, :detail)
                """).param("id", id).param("member", member).param("kind", kind.name())
                .param("institution", kind == Kind.INSTITUTION ? null : institution)
                .param("name", name == null ? null : name.strip()).param("detail", detail == null ? null : detail.strip())
                .update();
        return id;
    }

    List<DirectoryRequest> requests(UUID member) {
        if (member == null) {
            return db.sql("""
                    select id, member_id, kind, institution_id, name, detail, status from institutions.request
                    where status = 'PENDING' order by created_at limit 100
                    """).query(DirectoryRequest.class).list();
        }
        return db.sql("""
                select id, member_id, kind, institution_id, name, detail, status from institutions.request
                where member_id = :member order by created_at desc limit 100
                """).param("member", member).query(DirectoryRequest.class).list();
    }

    @Transactional
    public void review(UUID id, UUID moderator, boolean approved, String verifiedValue) {
        var request = db.sql("""
                update institutions.request set status = :status, reviewed_by = :moderator
                where id = :id and status = 'PENDING'
                returning id, member_id, kind, institution_id, name, detail, status
                """).param("status", approved ? "APPROVED" : "REJECTED").param("moderator", moderator)
                .param("id", id).query(DirectoryRequest.class).optional()
                .orElseThrow(() -> ApiFault.conflict("This request is no longer pending."));
        if (!approved) return;

        switch (request.kind()) {
            case INSTITUTION -> {
                var domain = verifiedValue.strip().toLowerCase(java.util.Locale.ROOT);
                if (!domain.isEmpty() && !domain.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}")) {
                    throw ApiFault.badRequest("Enter an official email domain such as college.edu.");
                }
                db.sql("insert into institutions.institution(id, name, email_domain) values (:id, :name, :domain)")
                        .param("id", UUID.randomUUID()).param("name", request.name())
                        .param("domain", domain.isEmpty() ? null : domain).update();
            }
            case COURSE -> {
                var code = verifiedValue.strip();
                if (code.isEmpty() || code.length() > 30) throw ApiFault.badRequest("Enter the official course code.");
                db.sql("insert into institutions.course(id, institution_id, code, name) values (:id, :college, :code, :name)")
                        .param("id", UUID.randomUUID()).param("college", request.institutionId())
                        .param("code", code).param("name", request.name()).update();
            }
            case AFFILIATION -> affiliate(request.memberId(), request.institutionId());
        }
    }

    private void affiliate(UUID member, UUID institution) {
        db.sql("""
                insert into institutions.affiliation(member_id, institution_id)
                values (:member, :institution)
                on conflict (member_id) do update set institution_id = excluded.institution_id, verified_at = now()
                """).param("member", member).param("institution", institution).update();
    }

    enum Kind { INSTITUTION, COURSE, AFFILIATION }
    record Institution(UUID id, String name) {}
    record Course(UUID id, String code, String name) {}
    record DirectoryRequest(UUID id, UUID memberId, Kind kind, UUID institutionId, String name, String detail, String status) {}
}
