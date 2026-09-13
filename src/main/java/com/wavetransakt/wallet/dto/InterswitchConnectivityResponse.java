package com.wavetransakt.wallet.dto;

/**
 * Safe response for a controlled Interswitch OAuth connectivity probe.
 *
 * This DTO intentionally contains no access token, client identifier, secret,
 * wallet identifier, phone number, NIN/BVN, or raw provider payload.
 */
public record InterswitchConnectivityResponse(
        boolean configured,
        boolean authenticationReachable,
        String status,
        String message
) {
}
