package com.wavetransakt.serviceprovider.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safe capability discovery for the Android services browser.
 * Never exposes VTpass API, secret or public key values.
 *
 * Configured means keys were supplied; it does NOT imply credentials are
 * entitled, funded or approved by the provider.
 */
@RestController
@RequestMapping("/api/v1/services/vtpass")
public class VtpassReadinessController {

    @Value("${vtpass.base-url:https://sandbox.vtpass.com/api/}")
    private String baseUrl;

    @Value("${vtpass.api-key:}")
    private String apiKey;

    @Value("${vtpass.public-key:}")
    private String publicKey;

    @Value("${vtpass.secret-key:}")
    private String secretKey;

    @Value("${wave.services.provider-payments-enabled:false}")
    private boolean paymentsEnabled;

    @GetMapping("/readiness")
    public ResponseEntity<Readiness> readiness() {
        String url = baseUrl == null ? "" : baseUrl.trim();
        String environment = url.equals("https://sandbox.vtpass.com/api/") ? "SANDBOX"
                : url.equals("https://vtpass.com/api/") ? "LIVE"
                : "UNRECOGNIZED";

        boolean catalogConfigured = !environment.equals("UNRECOGNIZED")
                && hasText(apiKey) && hasText(publicKey);
        boolean purchaseConfigured = catalogConfigured && hasText(secretKey);
        boolean purchasesAvailable = purchaseConfigured && paymentsEnabled;

        String message = !catalogConfigured
                ? "Services catalog is unavailable until provider credentials are configured."
                : purchasesAvailable
                ? "Provider credentials and payment switch are configured. Provider approvals and settlement must still be verified."
                : "Browse supported services; purchases remain unavailable.";

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new Readiness(
                        environment,
                        catalogConfigured,
                        purchaseConfigured,
                        purchasesAvailable,
                        message
                ));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Readiness(
            String environment,
            boolean catalogConfigured,
            boolean purchaseConfigured,
            boolean purchasesEnabled,
            String message
    ) {}
}
