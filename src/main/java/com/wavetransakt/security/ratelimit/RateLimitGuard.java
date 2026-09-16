package com.wavetransakt.security.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class RateLimitGuard {

    private final DistributedRateLimitService rateLimitService;

    public RateLimitGuard(DistributedRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    public void requireAllowed(
            String policyCode,
            String subject,
            int limit,
            Duration window
    ) {
        DistributedRateLimitService.RateLimitDecision decision =
                rateLimitService.consume(policyCode, subject, limit, window);

        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    /**
     * Enforces a previously accumulated failure lock without incrementing the
     * distributed bucket. Use this before failure-only authorization checks.
     */
    public void requireNotBlocked(
            String policyCode,
            String subject,
            int limit,
            Duration window
    ) {
        DistributedRateLimitService.RateLimitStatus status =
                rateLimitService.status(policyCode, subject, limit, window);

        if (status.blocked()) {
            throw new RateLimitExceededException(status.retryAfterSeconds());
        }
    }

    public String canonicalIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "MISSING";
        }
        String normalized = identifier.trim();
        if (normalized.contains("@")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }
}
