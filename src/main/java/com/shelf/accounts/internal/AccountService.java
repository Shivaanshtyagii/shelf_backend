package com.shelf.accounts.internal;

import com.shelf.accounts.Accounts;
import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
class AccountService implements Accounts {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient db;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final String dummyHash;

    AccountService(JdbcClient db, PasswordEncoder encoder, ApplicationEventPublisher events) {
        this.db = db;
        this.encoder = encoder;
        this.events = events;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public void register(String rawEmail, String name, String password) {
        var email = email(rawEmail);
        var id = UUID.randomUUID();
        var count = db.sql("""
                insert into accounts.member(id, email, display_name, password_hash)
                values (:id, :email, :name, :password) on conflict (email) do nothing
                """).param("id", id).param("email", email).param("name", name.strip())
                .param("password", encode(password)).update();
        if (count == 1) issueChallenge(id, email, "VERIFY");
    }

    Authentication login(String rawEmail, String password) {
        var entry = byEmail(email(rawEmail));
        var hash = entry.map(Credentials::passwordHash).orElse(dummyHash);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 || !encoder.matches(password, hash) || entry.isEmpty()) {
            throw new ApiFault(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        var credentials = entry.orElseThrow();
        return UsernamePasswordAuthenticationToken.authenticated(
                new SessionIdentity(credentials.id(), credentials.credentialVersion()), null, List.of());
    }

    @Override
    public Member current(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof SessionIdentity identity)) {
            throw new ApiFault(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        return db.sql("""
                select id, email, display_name, verified, role from accounts.member
                where id = :id and credential_version = :version
                """).param("id", identity.id()).param("version", identity.version()).query(Member.class).optional()
                .orElseThrow(() -> new ApiFault(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }

    @Override
    public Member verified(Principal principal) {
        var member = current(principal);
        if (!member.verified()) throw ApiFault.forbidden("Verify your account email to continue.");
        return member;
    }

    @Override
    public Member moderator(Principal principal) {
        var member = verified(principal);
        if (!member.moderator()) throw ApiFault.forbidden("Moderator access is required.");
        return member;
    }

    Member updateProfile(Principal principal, String name) {
        var member = current(principal);
        db.sql("update accounts.member set display_name = :name where id = :id")
                .param("name", name.strip()).param("id", member.id()).update();
        return current(principal);
    }

    @Transactional
    public void requestChallenge(String rawEmail, String purpose) {
        byEmail(email(rawEmail)).filter(entry -> !purpose.equals("VERIFY") || !entry.verified())
                .ifPresent(entry -> issueChallenge(entry.id(), email(rawEmail), purpose));
    }

    @Transactional
    public void confirm(String token, String purpose, String password) {
        var id = db.sql("""
                delete from accounts.challenge where token_hash = :hash
                  and purpose = :purpose and expires_at > now()
                returning member_id
                """).param("hash", digest(token)).param("purpose", purpose).query(UUID.class).optional()
                .orElseThrow(() -> ApiFault.badRequest("This link has expired or is invalid."));
        if (purpose.equals("VERIFY")) {
            db.sql("update accounts.member set verified = true where id = :id").param("id", id).update();
        } else {
            db.sql("""
                    update accounts.member set password_hash = :hash, credential_version = credential_version + 1
                    where id = :id
                    """).param("id", id).param("hash", encode(password)).update();
        }
    }

    private Optional<Credentials> byEmail(String email) {
        return db.sql("select id, password_hash, verified, credential_version from accounts.member where email = :email")
                .param("email", email).query(Credentials.class).optional();
    }

    private void issueChallenge(UUID id, String email, String purpose) {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var minutes = purpose.equals("RESET") ? 30 : 1440;
        var written = db.sql("""
                insert into accounts.challenge(token_hash, member_id, purpose, expires_at)
                values (:hash, :id, :purpose, now() + make_interval(mins => :minutes))
                on conflict (member_id, purpose) do update
                    set token_hash = excluded.token_hash, expires_at = excluded.expires_at
                    where accounts.challenge.expires_at < excluded.expires_at - interval '1 minute'
                """).param("hash", digest(token)).param("id", id)
                .param("purpose", purpose).param("minutes", minutes).update();
        if (written == 1) events.publishEvent(new MailChallenge(email, purpose, token));
    }

    private String encode(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiFault.badRequest("Use a password of at most 72 UTF-8 bytes.");
        }
        return encoder.encode(password);
    }

    private static String email(String raw) { return raw.strip().toLowerCase(Locale.ROOT); }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record Credentials(UUID id, String passwordHash, boolean verified, int credentialVersion) {}
    record MailChallenge(String email, String purpose, String token) {}
    record SessionIdentity(UUID id, int version) implements AuthenticatedPrincipal, Serializable {
        @Override public String getName() { return id.toString(); }
    }
}
