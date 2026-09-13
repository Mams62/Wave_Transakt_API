package com.wavetransakt.wallet.dto;

/**
 * Safe controlled-stage diagnostics for the Interswitch integration.
 *
 * This response intentionally exposes configuration presence and host names
 * only. It must never include client IDs, secrets, access tokens, wallet IDs,
 * phone numbers, NIN/BVN values, or raw provider responses.
 */
public record InterswitchDiagnosticsResponse(
        boolean enabled,
        boolean configured,
        String passportHost,
        String walletHost,
        boolean clientIdConfigured,
        boolean clientSecretConfigured,
        String domain,
        String channel,
        String walletIdType,
        boolean moneyMovementEnabled
) {
}
