package com.wavetransakt.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedRateLimitServiceTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void countWithinLimitIsAllowed() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any(), any(), any(), any(), any()
        )).thenReturn(2);

        DistributedRateLimitService service =
                new DistributedRateLimitService(jdbcTemplate);

        DistributedRateLimitService.RateLimitDecision decision = service.consume(
                "AUTH_LOGIN_IDENTIFIER",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );

        assertTrue(decision.allowed());
        assertEquals(8, decision.limit());
        assertEquals(6, decision.remaining());
        assertEquals(0, decision.retryAfterSeconds());
        assertNotNull(decision.resetAt());
    }

    @Test
    void countAboveLimitIsDeniedWithRetryAfter() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any(), any(), any(), any(), any()
        )).thenReturn(9);

        DistributedRateLimitService service =
                new DistributedRateLimitService(jdbcTemplate);

        DistributedRateLimitService.RateLimitDecision decision = service.consume(
                "AUTH_LOGIN_IDENTIFIER",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );

        assertFalse(decision.allowed());
        assertEquals(0, decision.remaining());
        assertTrue(decision.retryAfterSeconds() > 0);
        assertTrue(decision.retryAfterSeconds() <= Duration.ofMinutes(10).toSeconds());
    }

    @Test
    void statusAtFailureThresholdIsBlockedWithoutIncrementingBucket() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any(), any(), any()
        )).thenReturn(5);

        DistributedRateLimitService service =
                new DistributedRateLimitService(jdbcTemplate);

        DistributedRateLimitService.RateLimitStatus status = service.status(
                "TRANSACTION_PIN_FAILURE",
                "USER:123",
                5,
                Duration.ofMinutes(10)
        );

        assertTrue(status.blocked());
        assertEquals(5, status.count());
        assertEquals(0, status.remaining());
        assertTrue(status.retryAfterSeconds() > 0);
        assertTrue(status.retryAfterSeconds() <= Duration.ofMinutes(10).toSeconds());
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void statusWithoutExistingBucketIsNotBlocked() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any(), any(), any()
        )).thenThrow(new EmptyResultDataAccessException(1));

        DistributedRateLimitService service =
                new DistributedRateLimitService(jdbcTemplate);

        DistributedRateLimitService.RateLimitStatus status = service.status(
                "TRANSACTION_PIN_FAILURE",
                "USER:123",
                5,
                Duration.ofMinutes(10)
        );

        assertFalse(status.blocked());
        assertEquals(0, status.count());
        assertEquals(5, status.remaining());
        assertEquals(0, status.retryAfterSeconds());
    }

    @Test
    void subjectHashIsDeterministicPolicyScopedAndDoesNotExposeRawSubject() {
        DistributedRateLimitService service =
                new DistributedRateLimitService(jdbcTemplate);

        String raw = "sensitive-person@example.com";
        String first = service.hashSubject("AUTH_LOGIN_IDENTIFIER", raw);
        String second = service.hashSubject("AUTH_LOGIN_IDENTIFIER", raw);
        String otherPolicy = service.hashSubject("AUTH_OTP_IDENTIFIER", raw);

        assertEquals(first, second);
        assertNotEquals(first, otherPolicy);
        assertEquals(64, first.length());
        assertFalse(first.contains(raw));
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void invalidLimitAndWindowAreRejectedBeforeDatabaseWrite() {
        DistributedRateLimitService service =
                new DistributedRateLimitService(jdbcTemplate);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.consume("AUTH_LOGIN", "subject", 0, Duration.ofMinutes(1))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.consume("AUTH_LOGIN", "subject", 5, Duration.ZERO)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.status("AUTH_LOGIN", "subject", 0, Duration.ofMinutes(1))
        );

        verifyNoInteractions(jdbcTemplate);
    }
}
