package com.wavetransakt.observability;

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
import java.util.regex.Pattern;

/**
 * Adds a bounded request correlation ID and safe API completion logging.
 *
 * This filter deliberately never logs request/response bodies, query strings,
 * authorization headers, cookies, account numbers, PINs, identity data or
 * provider credentials. When Spring MVC resolved a handler, logs use the route
 * template (for example /api/v1/transfers/bank/{reference}) rather than the
 * concrete request path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "wave.requestId";
    public static final String MDC_REQUEST_ID = "requestId";

    private static final Logger log = LoggerFactory.getLogger(ApiRequestContextFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        String previousRequestId = MDC.get(MDC_REQUEST_ID);
        long startedAt = System.nanoTime();
        boolean completedNormally = false;

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
            completedNormally = true;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            logCompletion(request, response, durationMs, completedNormally);
            restoreMdc(previousRequestId);
        }
    }

    String resolveRequestId(String candidate) {
        if (candidate != null) {
            String normalized = candidate.trim();
            if (SAFE_REQUEST_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return UUID.randomUUID().toString();
    }

    String safeRouteTemplate(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (route instanceof String pattern && pattern.startsWith("/")) {
            return pattern;
        }
        return "UNMATCHED";
    }

    private void logCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            long durationMs,
            boolean completedNormally
    ) {
        String route = safeRouteTemplate(request);
        if (!route.startsWith("/api/") || "/api/health".equals(route)) {
            return;
        }

        String outcome = completedNormally ? outcomeFor(response.getStatus()) : "EXCEPTION";
        log.info(
                "api_request method={} route={} status={} durationMs={} outcome={}",
                request.getMethod(),
                route,
                response.getStatus(),
                durationMs,
                outcome
        );
    }

    private String outcomeFor(int status) {
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        if (status >= 400) {
            return "CLIENT_ERROR";
        }
        return "SUCCESS";
    }

    private void restoreMdc(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(MDC_REQUEST_ID);
        } else {
            MDC.put(MDC_REQUEST_ID, previousRequestId);
        }
    }
}
