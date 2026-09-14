package com.wavetransakt.card.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterswitchCardIssuingGatewayTest {

    @Test
    void disabledProgramFailsClosed() {
        InterswitchCardIssuingGateway gateway = new InterswitchCardIssuingGateway(false, false, false);

        CardIssuingGateway.CardProgramReadiness readiness = gateway.readiness();
        assertFalse(readiness.configured());
        assertEquals("DISABLED", readiness.status());

        CardIssuingOperationException error = assertThrows(
                CardIssuingOperationException.class,
                () -> gateway.createCard(
                        new CardIssuingGateway.CardholderLinkCommand(
                                "USER-1",
                                "WALLET-1",
                                "WAVE-CONTACTLESS"
                        )
                )
        );

        assertTrue(error.getMessage().contains("not enabled"));
    }

    @Test
    void enabledFlagDoesNotBypassProviderApproval() {
        InterswitchCardIssuingGateway gateway = new InterswitchCardIssuingGateway(true, false, false);

        CardIssuingGateway.CardProgramReadiness readiness = gateway.readiness();
        assertTrue(readiness.configured());
        assertFalse(readiness.approved());
        assertFalse(readiness.cardCreationEnabled());
        assertEquals("AWAITING_PROVIDER_APPROVAL", readiness.status());

        CardIssuingOperationException error = assertThrows(
                CardIssuingOperationException.class,
                () -> gateway.activate(
                        new CardIssuingGateway.CardLifecycleCommand("CARD-REF-1", "TEST")
                )
        );

        assertTrue(error.getMessage().contains("product approval"));
    }

    @Test
    void providerApprovalStillDoesNotEnableOperationsWithoutApprovedTransport() {
        InterswitchCardIssuingGateway gateway = new InterswitchCardIssuingGateway(true, true, true);

        CardIssuingGateway.CardProgramReadiness readiness = gateway.readiness();
        assertTrue(readiness.approved());
        assertTrue(readiness.contactlessProgramApproved());
        assertFalse(readiness.cardCreationEnabled());
        assertFalse(readiness.activationEnabled());
        assertEquals("AWAITING_APPROVED_PROVIDER_CONTRACT", readiness.status());

        CardIssuingOperationException error = assertThrows(
                CardIssuingOperationException.class,
                () -> gateway.status(new CardIssuingGateway.CardStatusCommand("CARD-REF-1"))
        );

        assertTrue(error.getMessage().contains("transport is not configured"));
    }
}
