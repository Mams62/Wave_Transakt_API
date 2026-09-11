package com.wavetransakt.wallet.dto;

import java.time.LocalDateTime;

/**
 * Controlled-test diagnostics. This response contains configuration presence
 * only; it never returns API keys, subscription keys, NIN, BVN or OTP values.
 */
public record WemaDiagnosticsResponse(
        String gatewayHost,
        boolean gatewayLooksValid,
        boolean apiKeyConfigured,
        boolean subscriptionKeyConfigured,
        String onboardingStatus,
        boolean accountNumberAssigned,
        LocalDateTime lastSyncedAt
) {
}
