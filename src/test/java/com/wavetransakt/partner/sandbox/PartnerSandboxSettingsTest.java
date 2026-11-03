package com.wavetransakt.partner.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PartnerSandboxSettingsTest {
    @Test void failsClosedWithNoProviderConfiguration() {
        PartnerSandboxSettings settings = new PartnerSandboxSettings(new MockEnvironment());
        assertFalse(settings.simulationEnabled());
        assertFalse(settings.webhookVerificationReady());
        assertFalse(settings.realMoneyMovementEnabled());
        assertFalse(settings.bankApiContractVerified());
        assertThrows(ResponseStatusException.class, settings::requireSimulation);
        assertThrows(ResponseStatusException.class, settings::simulationSecret);
    }
    @Test void simulationNeverImpliesBankApprovalOrRealTransfers() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("wave.bank.wema.sandbox-simulation-enabled", "true")
                .withProperty("wave.bank.wema.sandbox-webhook-secret", "local-simulator-hmac-secret-123456789");
        PartnerSandboxSettings settings = new PartnerSandboxSettings(env);
        assertTrue(settings.simulationEnabled());
        assertTrue(settings.webhookVerificationReady());
        assertFalse(settings.bankApiContractVerified());
        assertFalse(settings.realMoneyMovementEnabled());
    }
}
