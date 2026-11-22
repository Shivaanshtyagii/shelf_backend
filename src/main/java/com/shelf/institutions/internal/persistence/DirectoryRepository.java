package com.shelf.institutions.internal.persistence;

import com.shelf.institutions.internal.model.Course;
import com.shelf.institutions.internal.model.DirectoryRequest;
import com.shelf.institutions.internal.model.DirectoryRequest.Kind;
import com.shelf.institutions.internal.model.Institution;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DirectoryRepository {
    private final JdbcClient db;

    DirectoryRepository(JdbcClient db) { this.db = db; }

    public Optional<UUID> verifiedInstitution(UUID memberId) {
        return db.sql("select institution_id from institutions.affiliation where member_id = :member")
                .param("member", memberId).query(UUID.class).optional();
    }

    public List<Institution> search(String search) {
        return db.sql("""
                select id, name from institutions.institution
                where position(lower(:search) in lower(name)) > 0 or :search = ''
                order by name, id limit 50
                """).param("search", search).query(Institution.class).list();
    }

    public List<Course> courses(UUID institution) {
        return db.sql("select id, code, name from institutions.course where institution_id = :id order by code limit 100")
                .param("id", institution).query(Course.class).list();
    }

    public Optional<String> officialDomain(UUID institution) {
        return db.sql("select email_domain from institutions.institution where id = :id")
                .param("id", institution).query(String.class).optional();
    }

    public void insertRequest(UUID id, UUID member, Kind kind, UUID institution, String name, String detail) {
        db.sql("""
                insert into institutions.request(id, member_id, kind, institution_id, name, detail)
                values (:id, :member, :kind, :institution, :name, :detail)
                """).param("id", id).param("member", member).param("kind", kind.name())
                .param("institution", institution).param("name", name).param("detail", detail).update();
    }

    public List<DirectoryRequest> pendingRequests() {
        return db.sql("""
                select id, member_id, kind, institution_id, name, detail, status from institutions.request
                where status = 'PENDING' order by created_at limit 100
                """).query(DirectoryRequest.class).list();
    }

    public List<DirectoryRequest> requestsFor(UUID member) {
        return db.sql("""
                select id, member_id, kind, institution_id, name, detail, status from institutions.request
                where member_id = :member order by created_at desc limit 100
                """).param("member", member).query(DirectoryRequest.class).list();
    }

    public Optional<DirectoryRequest> reviewRequest(UUID id, UUID moderator, String status) {
        return db.sql("""
                update institutions.request set status = :status, reviewed_by = :moderator
                where id = :id and status = 'PENDING'
                returning id, member_id, kind, institution_id, name, detail, status
                """).param("status", status).param("moderator", moderator).param("id", id)
                .query(DirectoryRequest.class).optional();
    }

    public void insertInstitution(UUID id, String name, String domain) {
        db.sql("insert into institutions.institution(id, name, email_domain) values (:id, :name, :domain)")
                .param("id", id).param("name", name).param("domain", domain).update();
    }

    public void insertCourse(UUID id, UUID institution, String code, String name) {
        db.sql("insert into institutions.course(id, institution_id, code, name) values (:id, :college, :code, :name)")
                .param("id", id).param("college", institution).param("code", code).param("name", name).update();
    }

    public void affiliate(UUID member, UUID institution) {
        db.sql("""
                insert into institutions.affiliation(member_id, institution_id)
                values (:member, :institution)
                on conflict (member_id) do update set institution_id = excluded.institution_id, verified_at = now()
                """).param("member", member).param("institution", institution).update();
    }
}
