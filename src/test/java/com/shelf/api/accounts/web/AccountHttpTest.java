package com.shelf.api.accounts.web;

import com.shelf.api.HttpTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class AccountHttpTest extends HttpTestSupport {
    @Autowired com.shelf.api.accounts.service.AccountService accounts;

    @Test
    void googleSignInUsesTheSubjectAndNeverClaimsAnExistingPasswordAccount() throws Exception {
        var subject = UUID.randomUUID().toString();
        var email = "google@" + domainA;
        var first = accounts.current(accounts.googleLogin(subject, email, "Student", true));
        assertThat(first.verified()).isTrue();
        assertThat(accounts.current(accounts.googleLogin(subject, email, "Student", true)).id()).isEqualTo(first.id());
        var passwordAccount = member("existing", collegeA);
        var existingEmail = JSON.readTree(passwordAccount.browser().get("/auth/me").body()).get("email").asText();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                accounts.googleLogin(UUID.randomUUID().toString(), existingEmail, "Student", true))
                .isInstanceOf(com.shelf.api.common.error.DomainException.class);
    }

    @Test
    void verificationAndPasswordResetUseSingleUseLinksAndInvalidateEarlierSessions() throws Exception {
        var email = "student@" + domainA;
        assertThat(guest.post("/auth/register", Map.of("email", email, "displayName", "Student", "password", PASSWORD)).statusCode())
                .isEqualTo(202);
        var verification = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(verification.capture());
        var verificationToken = token(verification.getValue());

        assertThat(guest.post("/auth/login", Map.of("email", email, "password", PASSWORD)).statusCode()).isEqualTo(200);
        assertThat(guest.post("/institutions/" + collegeA + "/verify", null).statusCode()).isEqualTo(403);
        assertThat(guest.post("/auth/verify", Map.of("token", verificationToken)).statusCode()).isEqualTo(204);
        assertThat(guest.post("/auth/verify", Map.of("token", verificationToken)).statusCode()).isEqualTo(400);
        assertThat(guest.post("/institutions/" + collegeA + "/verify", null).statusCode()).isEqualTo(200);

        assertThat(guest.post("/auth/reset-request", Map.of("email", email)).statusCode()).isEqualTo(202);
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, org.mockito.Mockito.times(2)).send(messages.capture());
        var resetToken = token(messages.getAllValues().getLast());
        var nextPassword = "another-password-43";
        assertThat(guest.post("/auth/reset", Map.of("token", resetToken, "password", nextPassword)).statusCode()).isEqualTo(204);
        assertThat(guest.get("/auth/me").statusCode()).isEqualTo(401);
        assertThat(guest.post("/auth/login", Map.of("email", email, "password", nextPassword)).statusCode()).isEqualTo(200);
    }
}
