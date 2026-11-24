package com.shelf.api.common.web;

import com.shelf.api.common.util.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final String HEADER = "X-Request-Id";

    public static void authenticated(UUID userId) {
        MDC.put("userId", userId.toString());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var previous = MDC.getCopyOfContextMap();
        var requestId = RequestIds.acceptOrGenerate(request.getHeader(HEADER));
        MDC.put("requestId", requestId);
        MDC.remove("userId");
        response.setHeader(HEADER, requestId);
        var start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            LOG.atInfo().addKeyValue("method", request.getMethod())
                    .addKeyValue("route", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                    .log("HTTP request");
            if (previous == null) MDC.clear();
            else MDC.setContextMap(previous);
        }
    }
}
