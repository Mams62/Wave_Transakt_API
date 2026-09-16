package com.wavetransakt.security;

import com.wavetransakt.security.ratelimit.RateLimitGuard;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shared authorization boundary for financial transaction PIN checks.
 *
 * Failed PIN checks across transfers, QR payments and service payments share
 * one per-user distributed failure budget. Successful PIN checks do not consume
 * that budget. PIN values are never logged, persisted or included in request
 * fingerprints by this component.
 */
@Service
@RequiredArgsConstructor
public class TransactionPinGuard {

    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{6}$");
    private static final String FAILURE_POLICY = "TRANSACTION_PIN_FAILURE";
    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitGuard rateLimitGuard;

    public User verify(UUID userId, String transactionPin) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found"));
        verify(user, transactionPin);
        return user;
    }

    public void verify(User user, String transactionPin) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        String pinHash = user.getTransactionPinHash();
        if (pinHash == null || pinHash.isBlank()) {
            throw new IllegalArgumentException("Transaction PIN is not configured");
        }

        if (transactionPin == null || !PIN_PATTERN.matcher(transactionPin).matches()) {
            recordFailure(user.getId());
            throw new IllegalArgumentException("Invalid transaction PIN");
        }

        if (!passwordEncoder.matches(transactionPin, pinHash)) {
            recordFailure(user.getId());
            throw new IllegalArgumentException("Invalid transaction PIN");
        }
    }

    private void recordFailure(UUID userId) {
        rateLimitGuard.requireAllowed(
                FAILURE_POLICY,
                "USER:" + userId,
                MAX_FAILURES,
                FAILURE_WINDOW
        );
    }
}
