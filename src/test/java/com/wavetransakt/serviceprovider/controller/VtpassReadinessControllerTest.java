package com.wavetransakt.serviceprovider.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class VtpassReadinessControllerTest {

    @Test
    void missingCredentialsLeaveCatalogAndPurchasesDisabled() {
        VtpassReadinessController controller = controller(
                "https://sandbox.vtpass.com/api/", "", "", "", false);
        var result = controller.readiness();
        assertEquals("no-store", result.getHeaders().getCacheControl());
        var readiness = result.getBody();
        assertNotNull(readiness);
        assertEquals("SANDBOX", readiness.environment());
        assertFalse(readiness.catalogConfigured());
        assertFalse(readiness.purchasesEnabled());
    }

    @Test
    void catalogOnlyCredentialsNeverEnablePurchases() {
        var readiness = controller(
                "https://sandbox.vtpass.com/api/", "api", "public", "", true)
                .readiness().getBody();
        assertNotNull(readiness);
        assertTrue(readiness.catalogConfigured());
        assertFalse(readiness.purchaseConfigured());
        assertFalse(readiness.purchasesEnabled());
    }

    @Test
    void purchaseSwitchOffKeepsPurchasesDisabled() {
        var readiness = controller(
                "https://sandbox.vtpass.com/api/", "api", "public", "secret", false)
                .readiness().getBody();
        assertNotNull(readiness);
        assertTrue(readiness.purchaseConfigured());
        assertFalse(readiness.purchasesEnabled());
    }

    @Test
    void unrecognizedBaseUrlNeverEnablesServices() {
        var readiness = controller(
                "https://unexpected.example/api/", "api", "public", "secret", true)
                .readiness().getBody();
        assertNotNull(readiness);
        assertEquals("UNRECOGNIZED", readiness.environment());
        assertFalse(readiness.catalogConfigured());
        assertFalse(readiness.purchasesEnabled());
    }

    private VtpassReadinessController controller(
            String base, String api, String publicKey, String secret, boolean enabled) {
        var controller = new VtpassReadinessController();
        ReflectionTestUtils.setField(controller, "baseUrl", base);
        ReflectionTestUtils.setField(controller, "apiKey", api);
        ReflectionTestUtils.setField(controller, "publicKey", publicKey);
        ReflectionTestUtils.setField(controller, "secretKey", secret);
        ReflectionTestUtils.setField(controller, "paymentsEnabled", enabled);
        return controller;
    }
}
