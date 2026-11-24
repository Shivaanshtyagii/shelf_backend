package com.shelf.api.accounts.integration;

import com.shelf.api.accounts.service.NeonMembers;
import com.shelf.api.common.error.SecurityErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

class NeonBearerFilter extends OncePerRequestFilter {
    private final JwtConsumer tokens;
    private final NeonMembers members;

    private final SecurityErrors errors;

    NeonBearerFilter(JwtConsumer tokens, NeonMembers members, SecurityErrors errors) {
        this.errors = errors;
        this.tokens = tokens;
        this.members = members;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ")) {
            errors.commence(request, response, new BadCredentialsException("Invalid bearer token"));
            return;
        }
        try {
            var claims = tokens.processToClaims(header.substring(7));
            var email = claims.getStringClaimValue("email");
            if (email == null || email.isBlank() || email.length() > 254
                    || Boolean.TRUE.equals(claims.getClaimValue("banned"))) {
                errors.commence(request, response, new BadCredentialsException("Invalid bearer token"));
                return;
            }
            var member = members.resolve(claims.getSubject(), email, claims.getStringClaimValue("name"),
                    Boolean.TRUE.equals(claims.getClaimValue("emailVerified")));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(member, null, List.of()));
            SecurityContextHolder.setContext(context);
        } catch (InvalidJwtException | MalformedClaimException ex) {
            errors.commence(request, response, new BadCredentialsException("Invalid bearer token"));
            return;
        } catch (RuntimeException ex) {
            errors.resolve(request, response, ex);
            return;
        }
        chain.doFilter(request, response);
    }
}
