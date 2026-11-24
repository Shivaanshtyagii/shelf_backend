package com.shelf.api.accounts.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.accounts.api.Member;
import com.shelf.api.accounts.domain.AccountCredentials;
import com.shelf.api.accounts.domain.ChallengePurpose;
import com.shelf.api.accounts.domain.MailChallenge;
import com.shelf.api.accounts.domain.SessionIdentity;
import com.shelf.api.accounts.repository.AccountRepository;
import com.shelf.api.common.error.DomainException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService implements Accounts {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final String dummyHash;

    AccountService(AccountRepository accounts, PasswordEncoder encoder, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.events = events;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public void register(String rawEmail, String name, String password) {
        var email = email(rawEmail);
        var id = UUID.randomUUID();
        if (accounts.insertMember(id, email, name.strip(), encode(password))) {
            issueChallenge(id, email, ChallengePurpose.VERIFY);
        }
    }

    @Transactional
    public Authentication login(String rawEmail, String password) {
        var entry = accounts.credentials(email(rawEmail));
        var hash = entry.map(AccountCredentials::passwordHash).orElse(dummyHash);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 || !encoder.matches(password, hash)
                || entry.isEmpty() || entry.get().passwordHash() == null) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        var credentials = entry.orElseThrow();
        if (!credentials.verified()) {
            issueChallenge(credentials.id(), email(rawEmail), ChallengePurpose.VERIFY);
        }
        return authenticated(credentials);
    }

    @Transactional
    public Authentication googleLogin(String subject, String rawEmail, String name, boolean authoritative) {
        var existing = accounts.googleCredentials(subject);
        if (existing.isPresent()) return authenticated(existing.get());
        var email = email(rawEmail);
        var display = name == null || name.isBlank() ? email : name.strip();
        var id = UUID.randomUUID();
        accounts.insertGoogleMember(id, subject, email, display.substring(0, Math.min(display.length(), 80)),
                encoder.encode(UUID.randomUUID().toString()), authoritative);
        var credentials = accounts.googleCredentials(subject)
                .orElseThrow(() -> DomainException.conflict("Sign in using your existing account."));
        if (!authoritative && id.equals(credentials.id())) issueChallenge(id, email, ChallengePurpose.VERIFY);
        return authenticated(credentials);
    }

    private Authentication authenticated(AccountCredentials credentials) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new SessionIdentity(credentials.id(), credentials.credentialVersion()), null, List.of());
    }

    @Override
    public Member current(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof Member member) return member;
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof SessionIdentity identity)) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        return accounts.memberForSession(identity.id(), identity.version())
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }

    @Override
    public Member verified(Principal principal) {
        var member = current(principal);
        if (!member.verified()) throw DomainException.forbidden("Verify your account email to continue.");
        return member;
    }

    @Override
    public Member moderator(Principal principal) {
        var member = verified(principal);
        if (!member.moderator()) throw DomainException.forbidden("Moderator access is required.");
        return member;
    }

    public Member updateProfile(Principal principal, String name) {
        var member = current(principal);
        accounts.updateDisplayName(member.id(), name.strip());
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Member) {
            return new Member(member.id(), member.email(), name.strip(), member.verified(), member.role());
        }
        return current(principal);
    }

    @Transactional
    public void requestChallenge(String rawEmail, ChallengePurpose purpose) {
        var email = email(rawEmail);
        accounts.credentials(email).filter(entry -> entry.passwordHash() != null)
                .filter(entry -> purpose != ChallengePurpose.VERIFY || !entry.verified())
                .ifPresent(entry -> issueChallenge(entry.id(), email, purpose));
    }

    @Transactional
    public void confirm(String token, ChallengePurpose purpose, String password) {
        var id = accounts.consumeChallenge(digest(token), purpose)
                .orElseThrow(() -> DomainException.badRequest("This link has expired or is invalid."));
        switch (purpose) {
            case VERIFY -> accounts.markVerified(id);
            case RESET -> accounts.changePassword(id, encode(password));
        }
    }

    private void issueChallenge(UUID id, String email, ChallengePurpose purpose) {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (accounts.saveChallenge(id, purpose, digest(token), purpose.validityMinutes())) {
            events.publishEvent(new MailChallenge(email, purpose, token));
        }
    }

    private String encode(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw DomainException.badRequest("Use a password of at most 72 UTF-8 bytes.");
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
}
