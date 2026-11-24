package com.shelf.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Security filters run before MVC. Dispatch their errors to the same controller advice. */
@Component
public class SecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final HandlerExceptionResolver resolver;

    public SecurityErrors(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    public void resolve(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        resolver.resolveException(request, response, null, ex);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
        resolve(request, response, ex);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
        resolve(request, response, ex);
    }
}
