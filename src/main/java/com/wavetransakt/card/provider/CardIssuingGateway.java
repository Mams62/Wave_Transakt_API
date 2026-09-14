package com.wavetransakt.card.provider;

/**
 * Provider-neutral boundary for a future issuer-backed Wave Transakt physical
 * card program.
 *
 * This interface intentionally models only opaque provider references and card
 * lifecycle state. PAN, CVV, track data, cryptographic keys and issuer secrets
 * do not belong in Wave application responses or logs.
 */
public interface CardIssuingGateway {

    String code();

    CardProgramReadiness readiness();

    CardholderLinkResult createCard(CardholderLinkCommand command);

    CardLifecycleResult activate(CardLifecycleCommand command);

    CardLifecycleResult block(CardLifecycleCommand command);

    CardLifecycleResult unblock(CardLifecycleCommand command);

    CardStatusResult status(CardStatusCommand command);

    record CardProgramReadiness(
            boolean configured,
            boolean approved,
            boolean cardCreationEnabled,
            boolean activationEnabled,
            boolean blockUnblockEnabled,
            boolean contactlessProgramApproved,
            String status
    ) {
    }

    record CardholderLinkCommand(
            String waveUserId,
            String waveWalletReference,
            String requestedProductCode
    ) {
    }

    record CardholderLinkResult(
            String provider,
            String providerCardReference,
            String maskedDisplayReference,
            String status,
            boolean contactlessEnabled
    ) {
    }

    record CardLifecycleCommand(
            String providerCardReference,
            String reason
    ) {
    }

    record CardLifecycleResult(
            String provider,
            String providerCardReference,
            String status
    ) {
    }

    record CardStatusCommand(String providerCardReference) {
    }

    record CardStatusResult(
            String provider,
            String providerCardReference,
            String status,
            boolean contactlessEnabled
    ) {
    }
}
