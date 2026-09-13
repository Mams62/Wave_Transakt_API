package com.wavetransakt.wallet.provider;

import com.wavetransakt.wallet.dto.WalletResponse;

import java.util.UUID;

/**
 * Provider-neutral boundary for the customer-facing Wave wallet.
 *
 * Android, iOS and POS clients should never depend on a bank or payment
 * provider implementation directly. Provider-specific integrations live
 * behind this interface so Wave can switch or add infrastructure without
 * changing the public wallet API.
 */
public interface WalletProvider {

    /** Stable internal provider code, for example WEMA or INTERSWITCH. */
    String code();

    /** Return the provider-backed view of the authenticated Wave wallet. */
    WalletResponse getWallet(UUID userId);
}
