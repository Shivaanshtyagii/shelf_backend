package com.shelf.accounts.internal.service;

import com.shelf.accounts.Member;
import com.shelf.accounts.internal.persistence.AccountRepository;
import com.shelf.common.ApiFault;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
@Profile("neon")
public class NeonMembers {
    private final AccountRepository accounts;

    NeonMembers(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Member resolve(String subject, String rawEmail, String name, boolean verified) {
        var email = rawEmail.strip().toLowerCase(Locale.ROOT);
        var member = accounts.neonMember(subject).orElseGet(() -> {
            var displayName = name == null || name.isBlank() ? email : name.strip();
            var end = displayName.offsetByCodePoints(0, Math.min(80, displayName.codePointCount(0, displayName.length())));
            accounts.insertNeonMember(UUID.randomUUID(), subject, email, displayName.substring(0, end));
            return accounts.neonMember(subject)
                    .orElseThrow(() -> ApiFault.conflict("This email belongs to an existing Shelf account. Contact Shelf to link it."));
        });
        if (!member.email().equals(email)) {
            throw ApiFault.conflict("Your sign-in email changed. Contact Shelf to update your account.");
        }
        return new Member(member.id(), member.email(), member.displayName(), verified, member.role());
    }
}
