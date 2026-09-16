package com.wavetransakt.security.ratelimit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Database-backed fixed-window limiter shared by all API instances.
 *
 * Raw subjects (IP addresses, user IDs, email/phone identifiers) are never
 * persisted. A policy-scoped SHA-256 digest is stored instead, which prevents
 * the rate-limit table from becoming another source of customer identifiers.
 */
@Service
public class DistributedRateLimitService {

    private static final Pattern POLICY_CODE_PATTERN =
            Pattern.compile("[A-Z0-9_:-]{3,64}");
    private static final long MAX_WINDOW_SECONDS = 86_400L;
    private static final int MAX_LIMIT = 100_000;
    private static final AtomicLong CLEANUP_COUNTER = new AtomicLong();

    private static final String CONSUME_SQL = """
            INSERT INTO api_rate_limit_buckets (
                policy_code,
                subject_hash,
                window_started_at,
                request_count,
                expires_at,
                updated_at
            ) VALUES (?, ?, ?, 1, ?, ?)
            ON CONFLICT (policy_code, subject_hash, window_started_at)
            DO UPDATE SET
                request_count = api_rate_limit_buckets.request_count + 1,
                expires_at = EXCLUDED.expires_at,
                updated_at = EXCLUDED.updated_at
            RETURNING request_count
            """;

    private final JdbcTemplate jdbcTemplate;

    public DistributedRateLimitService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Uses REQUIRES_NEW so failed authentication/financial requests still count
     * even when the caller's business transaction is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RateLimitDecision consume(
            String rawPolicyCode,
            String rawSubject,
            int limit,
            Duration window
    ) {
        String policyCode = normalizePolicyCode(rawPolicyCode);
        String subject = normalizeSubject(rawSubject);
        long windowSeconds = validateWindow(window);
        validateLimit(limit);

        Instant now = Instant.now();
        long currentEpochSecond = now.getEpochSecond();
        long windowStartEpoch = (currentEpochSecond / windowSeconds) * windowSeconds;
        long resetEpoch = windowStartEpoch + windowSeconds;

        Instant windowStart = Instant.ofEpochSecond(windowStartEpoch);
        Instant resetAt = Instant.ofEpochSecond(resetEpoch);
        String subjectHash = hashSubject(policyCode, subject);

        Integer count = jdbcTemplate.queryForObject(
                CONSUME_SQL,
                Integer.class,
                policyCode,
                subjectHash,
                Timestamp.from(windowStart),
                Timestamp.from(resetAt),
                Timestamp.from(now)
        );

        if (count == null || count < 1) {
            throw new IllegalStateException("Rate-limit counter update failed");
        }

        opportunisticCleanup(now);

        boolean allowed = count <= limit;
        int remaining = Math.max(0, limit - count);
        long retryAfterSeconds = allowed
                ? 0L
                : Math.max(1L, resetEpoch - currentEpochSecond);

        return new RateLimitDecision(
                allowed,
                limit,
                remaining,
                retryAfterSeconds,
                resetAt
        );
    }

    String hashSubject(String policyCode, String subject) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (policyCode + "\n" + subject)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizePolicyCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Rate-limit policy code is required");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!POLICY_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid rate-limit policy code");
        }
        return normalized;
    }

    private String normalizeSubject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Rate-limit subject is required");
        }
        String normalized = raw.trim();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("Rate-limit subject is too long");
        }
        return normalized;
    }

    private long validateWindow(Duration window) {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate-limit window must be positive");
        }
        long seconds = window.getSeconds();
        if (seconds < 1 || seconds > MAX_WINDOW_SECONDS) {
            throw new IllegalArgumentException("Rate-limit window is out of range");
        }
        return seconds;
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Rate-limit request limit is out of range");
        }
    }

    private void opportunisticCleanup(Instant now) {
        if ((CLEANUP_COUNTER.incrementAndGet() & 1023L) != 0L) {
            return;
        }
        jdbcTemplate.update(
                "DELETE FROM api_rate_limit_buckets WHERE expires_at < ?",
                Timestamp.from(now.minus(Duration.ofHours(1)))
        );
    }

    public record RateLimitDecision(
            boolean allowed,
            int limit,
            int remaining,
            long retryAfterSeconds,
            Instant resetAt
    ) {
    }
}
