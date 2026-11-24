package com.shelf.accounts.internal.persistence;

import com.shelf.accounts.Member;
import com.shelf.accounts.internal.model.AccountCredentials;
import com.shelf.accounts.internal.model.ChallengePurpose;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountRepository {
    private final JdbcClient db;

    AccountRepository(JdbcClient db) {
        this.db = db;
    }

    public boolean insertMember(UUID id, String email, String name, String passwordHash) {
        return db.sql("""
                insert into accounts.member(id, email, display_name, password_hash)
                values (:id, :email, :name, :password) on conflict (email) do nothing
                """).param("id", id).param("email", email).param("name", name)
                .param("password", passwordHash).update() == 1;
    }

    public Optional<AccountCredentials> credentials(String email) {
        return db.sql("select id, password_hash, verified, credential_version from accounts.member where email = :email")
                .param("email", email).query(AccountCredentials.class).optional();
    }

    public Optional<AccountCredentials> googleCredentials(String subject) {
        return db.sql("select id, password_hash, verified, credential_version from accounts.member where google_subject = :subject")
                .param("subject", subject).query(AccountCredentials.class).optional();
    }

    public void insertGoogleMember(UUID id, String subject, String email, String name, String hash, boolean verified) {
        db.sql("""
                insert into accounts.member(id, google_subject, email, display_name, password_hash, verified)
                values (:id, :subject, :email, :name, :hash, :verified) on conflict do nothing
                """).param("id", id).param("subject", subject).param("email", email)
                .param("name", name).param("hash", hash).param("verified", verified).update();
    }

    public Optional<Member> memberForSession(UUID id, int version) {
        return db.sql("""
                select id, email, display_name, verified, role from accounts.member
                where id = :id and credential_version = :version
                """).param("id", id).param("version", version).query(Member.class).optional();
    }

    public Optional<Member> neonMember(String subject) {
        return db.sql("select id, email, display_name, verified, role from accounts.member where neon_subject = :subject")
                .param("subject", subject).query(Member.class).optional();
    }

    public void insertNeonMember(UUID id, String subject, String email, String name) {
        db.sql("""
                insert into accounts.member(id, neon_subject, email, display_name, verified)
                values (:id, :subject, :email, :name, false)
                on conflict do nothing
                """).param("id", id).param("subject", subject).param("email", email).param("name", name)
                .update();
    }

    public void updateDisplayName(UUID member, String name) {
        db.sql("update accounts.member set display_name = :name where id = :id")
                .param("name", name).param("id", member).update();
    }

    public boolean saveChallenge(UUID id, ChallengePurpose purpose, String tokenHash, int minutes) {
        return db.sql("""
                insert into accounts.challenge(token_hash, member_id, purpose, expires_at)
                values (:hash, :id, :purpose, now() + make_interval(mins => :minutes))
                on conflict (member_id, purpose) do update
                    set token_hash = excluded.token_hash, expires_at = excluded.expires_at
                    where accounts.challenge.expires_at < excluded.expires_at - interval '1 minute'
                """).param("hash", tokenHash).param("id", id)
                .param("purpose", purpose.name()).param("minutes", minutes).update() == 1;
    }

    public Optional<UUID> consumeChallenge(String tokenHash, ChallengePurpose purpose) {
        return db.sql("""
                delete from accounts.challenge where token_hash = :hash
                  and purpose = :purpose and expires_at > now()
                returning member_id
                """).param("hash", tokenHash).param("purpose", purpose.name()).query(UUID.class).optional();
    }

    public void markVerified(UUID id) {
        db.sql("update accounts.member set verified = true where id = :id").param("id", id).update();
    }

    public void changePassword(UUID id, String passwordHash) {
        db.sql("""
                update accounts.member set password_hash = :hash, credential_version = credential_version + 1
                where id = :id
                """).param("id", id).param("hash", passwordHash).update();
    }
}
