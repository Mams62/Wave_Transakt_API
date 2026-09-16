package com.wavetransakt.security;

import com.wavetransakt.security.ratelimit.RateLimitExceededException;
import com.wavetransakt.security.ratelimit.RateLimitGuard;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionPinGuardTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RateLimitGuard rateLimitGuard;

    @Test
    void correctPinChecksLockButDoesNotConsumeFailureBudget() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "pin-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "pin-hash")).thenReturn(true);

        TransactionPinGuard guard = guard();
        User result = guard.verify(userId, "123456");

        assertEquals(user, result);
        verify(rateLimitGuard, times(2)).requireNotBlocked(
                "TRANSACTION_PIN_FAILURE",
                "USER:" + userId,
                5,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard, never()).requireAllowed(
                anyString(), anyString(), anyInt(), any(Duration.class)
        );
    }

    @Test
    void wrongPinConsumesSharedUserFailureBudget() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "pin-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("654321", "pin-hash")).thenReturn(false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> guard().verify(userId, "654321")
        );

        assertEquals("Invalid transaction PIN", error.getMessage());
        InOrder order = inOrder(rateLimitGuard, passwordEncoder);
        order.verify(rateLimitGuard).requireNotBlocked(
                "TRANSACTION_PIN_FAILURE",
                "USER:" + userId,
                5,
                Duration.ofMinutes(10)
        );
        order.verify(passwordEncoder).matches("654321", "pin-hash");
        order.verify(rateLimitGuard).requireAllowed(
                "TRANSACTION_PIN_FAILURE",
                "USER:" + userId,
                5,
                Duration.ofMinutes(10)
        );
    }

    @Test
    void malformedPinAlsoConsumesFailureBudgetWithoutHashCheck() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "pin-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(
                IllegalArgumentException.class,
                () -> guard().verify(userId, "12")
        );

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(rateLimitGuard).requireNotBlocked(
                "TRANSACTION_PIN_FAILURE",
                "USER:" + userId,
                5,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard).requireAllowed(
                "TRANSACTION_PIN_FAILURE",
                "USER:" + userId,
                5,
                Duration.ofMinutes(10)
        );
    }

    @Test
    void activeFailureLockBlocksEvenCorrectPinBeforeHashCheck() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "pin-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new RateLimitExceededException(120))
                .when(rateLimitGuard).requireNotBlocked(
                        "TRANSACTION_PIN_FAILURE",
                        "USER:" + userId,
                        5,
                        Duration.ofMinutes(10)
                );

        RateLimitExceededException error = assertThrows(
                RateLimitExceededException.class,
                () -> guard().verify(userId, "123456")
        );

        assertEquals(120, error.getRetryAfterSeconds());
        verifyNoInteractions(passwordEncoder);
        verify(rateLimitGuard, never()).requireAllowed(
                anyString(), anyString(), anyInt(), any(Duration.class)
        );
    }

    @Test
    void pinIsNeverPassedToRateLimitSubject() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "pin-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("999999", "pin-hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> guard().verify(userId, "999999"));

        verify(rateLimitGuard).requireAllowed(
                eq("TRANSACTION_PIN_FAILURE"),
                eq("USER:" + userId),
                eq(5),
                eq(Duration.ofMinutes(10))
        );
        verify(rateLimitGuard, never()).requireAllowed(
                anyString(),
                eq("999999"),
                anyInt(),
                any(Duration.class)
        );
        verify(rateLimitGuard, never()).requireNotBlocked(
                anyString(),
                eq("999999"),
                anyInt(),
                any(Duration.class)
        );
    }

    private TransactionPinGuard guard() {
        return new TransactionPinGuard(userRepository, passwordEncoder, rateLimitGuard);
    }

    private User user(UUID id, String hash) {
        return User.builder()
                .id(id)
                .email("person@example.com")
                .transactionPinHash(hash)
                .build();
    }
}
