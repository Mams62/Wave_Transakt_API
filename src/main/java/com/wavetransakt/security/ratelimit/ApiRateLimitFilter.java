package com.wavetransakt.security.ratelimit;

import com.wavetransakt.observability.ApiRequestContextFilter;
import com.wavetransakt.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Applies coarse source throttles to public authentication endpoints and
 * user-scoped throttles to provider/financial operations.
 *
 * The concrete URI is used only for in-process rule matching and is never
 * logged or persisted. Provider webhook endpoints are intentionally excluded;
 * they need provider-specific signature/retry policies before throttling.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);

    private static final Pattern BANK_REQUERY =
            Pattern.compile("^/api/v1/transfers/bank/[^/]+/requery$");
    private static final Pattern SERVICE_REQUERY =
            Pattern.compile("^/api/v1/services/payments/[^/]+/requery$");
    private static final Pattern WALLET_QR_RESOLVE =
            Pattern.compile("^/api/v1/qr/wallet/[^/]+$");
    private static final Pattern ADMIN_RECON_ACTION =
            Pattern.compile("^/api/v1/admin/funding-reconciliation/events/[^/]+/actions$");

    private final DistributedRateLimitService rateLimitService;
    private final boolean enabled;

    public ApiRateLimitFilter(
            DistributedRateLimitService rateLimitService,
            @Value("${wave.security.rate-limits-enabled:true}") boolean enabled
    ) {
        this.rateLimitService = rateLimitService;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        RateRule rule = ruleFor(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = rule.subjectMode() == SubjectMode.USER
                ? authenticatedUserSubject(request)
                : sourceSubject(request);

        try {
            DistributedRateLimitService.RateLimitDecision decision =
                    rateLimitService.consume(
                            rule.policyCode(),
                            subject,
                            rule.limit(),
                            rule.window()
                    );

            response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
            response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));

            if (!decision.allowed()) {
                writeRateLimited(response, decision.retryAfterSeconds());
                return;
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "rate_limit_unavailable policy={} error={}",
                    rule.policyCode(),
                    exception.getClass().getSimpleName()
            );
            writeRateLimitUnavailable(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    RateRule ruleFor(String method, String uri) {
        if (method == null || uri == null) {
            return null;
        }

        if ("POST".equals(method)) {
            return switch (uri) {
                case "/api/auth/register" -> source("AUTH_REGISTER_SOURCE", 10, Duration.ofMinutes(10));
                case "/api/auth/login" -> source("AUTH_LOGIN_SOURCE", 60, Duration.ofMinutes(5));
                case "/api/auth/login/otp" -> source("AUTH_OTP_SOURCE", 60, Duration.ofMinutes(5));
                case "/api/auth/login/face/start",
                        "/api/auth/login/face/capture",
                        "/api/auth/login/face/complete" -> source("AUTH_FACE_SOURCE", 90, Duration.ofMinutes(5));
                case "/api/auth/password/forgot" -> source("RECOVERY_REQUEST_SOURCE", 30, Duration.ofMinutes(15));
                case "/api/auth/password/reset" -> source("RECOVERY_RESET_SOURCE", 60, Duration.ofMinutes(15));
                case "/api/v1/pos/pairing/redeem" -> source("POS_PAIRING_REDEEM_SOURCE", 30, Duration.ofMinutes(5));
                case "/api/verification/email" -> source("EMAIL_VERIFY_SOURCE", 60, Duration.ofMinutes(10));
                case "/api/verification/email/resend" -> source("EMAIL_RESEND_SOURCE", 20, Duration.ofMinutes(15));
                case "/api/transactions/transfer",
                        "/api/v1/qr/payment/pay",
                        "/api/v1/qr/wallet/pay",
                        "/api/v1/services/pay",
                        "/api/v1/services/pay/internet/smile",
                        "/api/v1/services/pay/education/waec-result-checker",
                        "/api/v1/services/pay/education/waec-registration",
                        "/api/v1/services/pay/education/jamb" -> user("MONEY_WRITE_USER", 20, Duration.ofMinutes(1));
                case "/api/v1/transfers/bank" -> user("BANK_TRANSFER_USER", 10, Duration.ofMinutes(1));
                case "/api/v1/qr/payment/create" -> user("QR_CREATE_USER", 30, Duration.ofMinutes(1));
                case "/api/v1/qr/payment/resolve" -> user("QR_RESOLVE_USER", 120, Duration.ofMinutes(1));
                default -> {
                    if (BANK_REQUERY.matcher(uri).matches() || SERVICE_REQUERY.matcher(uri).matches()) {
                        yield user("PROVIDER_REQUERY_USER", 30, Duration.ofMinutes(1));
                    }
                    if (ADMIN_RECON_ACTION.matcher(uri).matches()) {
                        yield user("ADMIN_RECON_ACTION_USER", 60, Duration.ofMinutes(1));
                    }
                    yield null;
                }
            };
        }

        if ("GET".equals(method)) {
            if ("/api/v1/transfers/bank/resolve".equals(uri)) {
                return user("BANK_NAME_ENQUIRY_USER", 30, Duration.ofMinutes(1));
            }
            if (WALLET_QR_RESOLVE.matcher(uri).matches()) {
                return user("QR_RESOLVE_USER", 120, Duration.ofMinutes(1));
            }
        }

        return null;
    }

    private RateRule source(String policyCode, int limit, Duration window) {
        return new RateRule(policyCode, limit, window, SubjectMode.SOURCE);
    }

    private RateRule user(String policyCode, int limit, Duration window) {
        return new RateRule(policyCode, limit, window, SubjectMode.USER);
    }

    private String authenticatedUserSubject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null &&
                authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof User user &&
                user.getId() != null) {
            return "USER:" + user.getId();
        }
        return sourceSubject(request);
    }

    private String sourceSubject(HttpServletRequest request) {
        /*
         * application.yml uses server.forward-headers-strategy=framework, so
         * Spring normalizes trusted proxy forwarding information before this
         * filter sees the request. Deliberately do not parse X-Forwarded-For
         * here: the limiter should consume only the framework-normalized
         * remote address and never trust a raw client-supplied header itself.
         */
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            remoteAddress = "UNKNOWN";
        }
        return "SOURCE:" + remoteAddress;
    }

    private void writeRateLimited(
            HttpServletResponse response,
            long retryAfterSeconds
    ) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(Math.max(1L, retryAfterSeconds)));
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
                "{\"status\":429,\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again later.\"}"
        );
    }

    private void writeRateLimitUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(503);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        Object requestId = response.getHeader(ApiRequestContextFilter.REQUEST_ID_HEADER);
        String suffix = requestId == null
                ? ""
                : ",\"requestId\":\"" + escapeJson(requestId.toString()) + "\"";
        response.getWriter().write(
                "{\"status\":503,\"error\":\"SECURITY_THROTTLE_UNAVAILABLE\",\"message\":\"A required security service is temporarily unavailable.\"" + suffix + "}"
        );
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    enum SubjectMode {
        SOURCE,
        USER
    }

    record RateRule(
            String policyCode,
            int limit,
            Duration window,
            SubjectMode subjectMode
    ) {
    }
}
