package com.wavetransakt.merchant.provider;

/**
 * Provider-neutral boundary for Wave Business acquiring/POS infrastructure.
 *
 * It is deliberately separate from the consumer WalletProvider boundary.
 * No payment execution method is exposed until a real acquiring contract,
 * certified terminal flow and settlement/reconciliation controls exist.
 */
public interface MerchantAcquiringProvider {

    String code();

    Capabilities capabilities();

    record Capabilities(
            boolean configured,
            boolean merchantOnboardingAvailable,
            boolean qrAcceptanceAvailable,
            boolean cardAcceptanceAvailable,
            boolean contactlessAvailable,
            boolean settlementAvailable,
            boolean reportingAvailable,
            String status
    ) {
    }
}
