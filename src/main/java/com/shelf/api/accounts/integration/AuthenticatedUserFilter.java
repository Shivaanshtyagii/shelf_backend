package com.shelf.api.accounts.integration;

import com.shelf.api.accounts.api.Member;
import com.shelf.api.accounts.domain.SessionIdentity;
import com.shelf.api.common.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs inside the security chain once the session or bearer principal is available. */
final class AuthenticatedUserFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            switch (auth.getPrincipal()) {
                case Member member -> RequestIdFilter.authenticated(member.id());
                case SessionIdentity identity -> RequestIdFilter.authenticated(identity.id());
                default -> { }
            }
        }
        chain.doFilter(request, response);
    }
}
