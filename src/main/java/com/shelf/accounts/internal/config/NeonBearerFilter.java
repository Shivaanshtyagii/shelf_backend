package com.shelf.accounts.internal.config;

import com.shelf.accounts.internal.service.NeonMembers;
import com.shelf.common.ApiFault;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

class NeonBearerFilter extends OncePerRequestFilter {
    private final JwtConsumer tokens;
    private final NeonMembers members;

    NeonBearerFilter(JwtConsumer tokens, NeonMembers members) {
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
            response.sendError(401);
            return;
        }
        try {
            var claims = tokens.processToClaims(header.substring(7));
            var email = claims.getStringClaimValue("email");
            if (email == null || email.isBlank() || email.length() > 254
                    || Boolean.TRUE.equals(claims.getClaimValue("banned"))) {
                response.sendError(401);
                return;
            }
            var member = members.resolve(claims.getSubject(), email, claims.getStringClaimValue("name"),
                    Boolean.TRUE.equals(claims.getClaimValue("emailVerified")));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(member, null, List.of()));
            SecurityContextHolder.setContext(context);
        } catch (InvalidJwtException | MalformedClaimException ex) {
            response.sendError(401);
            return;
        } catch (ApiFault ex) {
            response.setStatus(ex.status().value());
            response.setContentType("text/plain");
            response.getWriter().write(ex.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }
}
