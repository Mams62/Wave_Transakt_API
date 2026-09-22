package com.wavetransakt.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitGuardTest {

    @Mock
    DistributedRateLimitService rateLimitService;

    @Test
    void canonicalIdentifierNormalizesEmailAndNigerianPhoneAliases() {
        RateLimitGuard guard = new RateLimitGuard(rateLimitService);

        assertEquals("person@example.com", guard.canonicalIdentifier(" Person@Example.COM "));
        assertEquals("08012345678", guard.canonicalIdentifier(" 08012345678 "));
        assertEquals("08012345678", guard.canonicalIdentifier("+2348012345678"));
        assertEquals("08012345678", guard.canonicalIdentifier("2348012345678"));
        assertEquals("08012345678", guard.canonicalIdentifier("8012345678"));
        assertEquals("MISSING", guard.canonicalIdentifier("  "));
    }

    @Test
    void deniedDecisionThrowsRateLimitExceededWithRetryAfter() {
        Duration window = Duration.ofMinutes(10);
        when(rateLimitService.consume("AUTH_LOGIN_IDENTIFIER", "person@example.com", 8, window))
                .thenReturn(new DistributedRateLimitService.RateLimitDecision(
                        false,
                        8,
                        0,
                        120,
                        Instant.now().plusSeconds(120)
                ));

        RateLimitGuard guard = new RateLimitGuard(rateLimitService);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> guard.requireAllowed(
                        "AUTH_LOGIN_IDENTIFIER",
                        "person@example.com",
                        8,
                        window
                )
        );

        assertEquals(120, exception.getRetryAfterSeconds());
    }

    @Test
    void allowedDecisionReturnsNormally() {
        Duration window = Duration.ofMinutes(10);
        when(rateLimitService.consume("AUTH_LOGIN_IDENTIFIER", "person@example.com", 8, window))
                .thenReturn(new DistributedRateLimitService.RateLimitDecision(
                        true,
                        8,
                        7,
                        0,
                        Instant.now().plusSeconds(600)
                ));

        RateLimitGuard guard = new RateLimitGuard(rateLimitService);

        assertDoesNotThrow(() -> guard.requireAllowed(
                "AUTH_LOGIN_IDENTIFIER",
                "person@example.com",
                8,
                window
        ));
    }

    @Test
    void blockedStatusThrowsWithoutConsumingAnotherAttempt() {
        Duration window = Duration.ofMinutes(10);
        when(rateLimitService.status("TRANSACTION_PIN_FAILURE", "USER:123", 5, window))
                .thenReturn(new DistributedRateLimitService.RateLimitStatus(
                        true,
                        5,
                        5,
                        0,
                        90,
                        Instant.now().plusSeconds(90)
                ));

        RateLimitGuard guard = new RateLimitGuard(rateLimitService);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> guard.requireNotBlocked(
                        "TRANSACTION_PIN_FAILURE",
                        "USER:123",
                        5,
                        window
                )
        );

        assertEquals(90, exception.getRetryAfterSeconds());
        verify(rateLimitService, never()).consume(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void unblockedStatusReturnsNormally() {
        Duration window = Duration.ofMinutes(10);
        when(rateLimitService.status("TRANSACTION_PIN_FAILURE", "USER:123", 5, window))
                .thenReturn(new DistributedRateLimitService.RateLimitStatus(
                        false,
                        5,
                        3,
                        2,
                        0,
                        Instant.now().plusSeconds(300)
                ));

        RateLimitGuard guard = new RateLimitGuard(rateLimitService);

        assertDoesNotThrow(() -> guard.requireNotBlocked(
                "TRANSACTION_PIN_FAILURE",
                "USER:123",
                5,
                window
        ));
    }
}
