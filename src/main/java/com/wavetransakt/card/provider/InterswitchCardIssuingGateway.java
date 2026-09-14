package com.wavetransakt.card.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-closed Interswitch card-issuing boundary.
 *
 * Wave must not guess Card 360 / issuer API endpoints, product codes, BINs,
 * credential formats or lifecycle semantics. This gateway therefore remains
 * non-operational until the approved provider contract and transport are wired.
 */
@Component
public class InterswitchCardIssuingGateway implements CardIssuingGateway {

    private final boolean cardIssuingEnabled;
    private final boolean providerProductApproved;
    private final boolean contactlessProgramApproved;

    public InterswitchCardIssuingGateway(
            @Value("${interswitch.card.issuing-enabled:false}") boolean cardIssuingEnabled,
            @Value("${interswitch.card.product-approved:false}") boolean providerProductApproved,
            @Value("${interswitch.card.contactless-program-approved:false}") boolean contactlessProgramApproved
    ) {
        this.cardIssuingEnabled = cardIssuingEnabled;
        this.providerProductApproved = providerProductApproved;
        this.contactlessProgramApproved = contactlessProgramApproved;
    }

    @Override
    public String code() {
        return "INTERSWITCH";
    }

    @Override
    public CardProgramReadiness readiness() {
        boolean approvedAndEnabled = cardIssuingEnabled && providerProductApproved;
        return new CardProgramReadiness(
                cardIssuingEnabled,
                providerProductApproved,
                false,
                false,
                false,
                approvedAndEnabled && contactlessProgramApproved,
                readinessStatus()
        );
    }

    @Override
    public CardholderLinkResult createCard(CardholderLinkCommand command) {
        throw unavailable("card creation");
    }

    @Override
    public CardLifecycleResult activate(CardLifecycleCommand command) {
        throw unavailable("card activation");
    }

    @Override
    public CardLifecycleResult block(CardLifecycleCommand command) {
        throw unavailable("card blocking");
    }

    @Override
    public CardLifecycleResult unblock(CardLifecycleCommand command) {
        throw unavailable("card unblocking");
    }

    @Override
    public CardStatusResult status(CardStatusCommand command) {
        throw unavailable("card status inquiry");
    }

    private CardIssuingOperationException unavailable(String operation) {
        if (!cardIssuingEnabled) {
            return new CardIssuingOperationException(
                    "Interswitch card issuing is not enabled for " + operation
            );
        }
        if (!providerProductApproved) {
            return new CardIssuingOperationException(
                    "Interswitch card issuing product approval is required for " + operation
            );
        }
        return new CardIssuingOperationException(
                "Interswitch card issuing transport is not configured for " + operation
        );
    }

    private String readinessStatus() {
        if (!cardIssuingEnabled) {
            return "DISABLED";
        }
        if (!providerProductApproved) {
            return "AWAITING_PROVIDER_APPROVAL";
        }
        return "AWAITING_APPROVED_PROVIDER_CONTRACT";
    }
}
