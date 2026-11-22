package com.shelf.accounts.internal.config;

import com.shelf.accounts.internal.service.AccountService;
import com.shelf.common.ApiFault;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Profile("google")
class GoogleLoginHandler implements AuthenticationSuccessHandler {
    private final AccountService accounts;
    private final HttpSessionSecurityContextRepository sessions;

    GoogleLoginHandler(AccountService accounts, HttpSessionSecurityContextRepository sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication login) throws IOException {
        var user = (OidcUser) login.getPrincipal();
        try {
            if (user.getEmail() == null || !Boolean.TRUE.equals(user.getEmailVerified())) {
                throw ApiFault.forbidden("Verify your Google email first.");
            }
            var email = user.getEmail().toLowerCase(java.util.Locale.ROOT);
            var domain = email.substring(email.lastIndexOf('@') + 1);
            var auth = accounts.googleLogin(user.getSubject(), email, user.getFullName(),
                    domain.equals("gmail.com") || domain.equalsIgnoreCase(user.getClaimAsString("hd")));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            sessions.saveContext(context, request, response);
            response.setStatus(302);
            response.setHeader("Location", "/");
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            response.setStatus(302);
            response.setHeader("Location", "/account?error=google");
        }
    }
}
