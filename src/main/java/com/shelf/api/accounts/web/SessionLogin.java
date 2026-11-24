package com.shelf.api.accounts.web;

import com.shelf.api.accounts.domain.SessionIdentity;
import com.shelf.api.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
class SessionLogin {
    private final HttpSessionSecurityContextRepository sessions;

    SessionLogin(HttpSessionSecurityContextRepository sessions) {
        this.sessions = sessions;
    }

    void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        request.getSession();
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        sessions.saveContext(context, request, response);
        if (authentication.getPrincipal() instanceof SessionIdentity identity) {
            RequestIdFilter.authenticated(identity.id());
        }
    }
}
