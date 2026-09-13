package com.wavetransakt.merchant.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterswitchProvisioningReadinessTest {

    @Test
    void defaultConfigurationRemainsFailClosed() {
        InterswitchMerchantAcquiringProvider provider = new InterswitchMerchantAcquiringProvider(
                false,
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                false
        );

        InterswitchProvisioningReadiness readiness = new InterswitchProvisioningReadiness(
                provider,
                false,
                false,
                false,
                false,
                false
        );

        InterswitchProvisioningReadiness.Readiness snapshot = readiness.snapshot();

        assertEquals("INTERSWITCH", snapshot.provider());
        assertFalse(snapshot.providerConfigured());
        assertFalse(snapshot.merchantOnboardingReady());
        assertFalse(snapshot.terminalProvisioningReady());
        assertFalse(snapshot.contactlessReady());
        assertFalse(snapshot.reconciliationReady());
        assertFalse(snapshot.cardIssuingReady());
        assertTrue(snapshot.blockers().contains("INTERSWITCH_MERCHANT_PRODUCT_CONFIGURATION_REQUIRED"));
        assertTrue(snapshot.blockers().contains("SMARTPOS_TERMINAL_LINK_CONTRACT_REQUIRED"));
        assertTrue(snapshot.blockers().contains("CARD_ISSUING_PROGRAM_REQUIRED"));
    }

    @Test
    void readinessStagesRequireExplicitProviderGrants() {
        InterswitchMerchantAcquiringProvider provider = new InterswitchMerchantAcquiringProvider(
                true,
                "https://merchant.example.invalid",
                "configured-merchant-code",
                true,
                true,
                true,
                true,
                true,
                true
        );

        InterswitchProvisioningReadiness readiness = new InterswitchProvisioningReadiness(
                provider,
                true,
                true,
                true,
                true,
                true
        );

        InterswitchProvisioningReadiness.Readiness snapshot = readiness.snapshot();

        assertTrue(snapshot.providerConfigured());
        assertTrue(snapshot.merchantOnboardingReady());
        assertTrue(snapshot.terminalProvisioningReady());
        assertTrue(snapshot.contactlessReady());
        assertTrue(snapshot.reconciliationReady());
        assertTrue(snapshot.cardIssuingReady());
        assertTrue(snapshot.blockers().isEmpty());
    }
}
