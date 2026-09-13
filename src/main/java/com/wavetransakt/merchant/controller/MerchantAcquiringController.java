package com.wavetransakt.merchant.controller;

import com.wavetransakt.merchant.provider.InterswitchProvisioningReadiness;
import com.wavetransakt.merchant.provider.MerchantAcquiringProvider;
import com.wavetransakt.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Safe provider capability diagnostics for Wave Business.
 *
 * These endpoints report configuration/capability state only. They do not expose
 * credentials, merchant codes, terminal IDs, access tokens, provider payloads,
 * card data, settlement accounts or payment execution operations.
 */
@RestController
@RequestMapping("/api/v1/business/acquiring")
public class MerchantAcquiringController {

    private final List<MerchantAcquiringProvider> providers;
    private final InterswitchProvisioningReadiness interswitchProvisioningReadiness;

    public MerchantAcquiringController(
            List<MerchantAcquiringProvider> providers,
            InterswitchProvisioningReadiness interswitchProvisioningReadiness
    ) {
        this.providers = providers;
        this.interswitchProvisioningReadiness = interswitchProvisioningReadiness;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<List<ProviderCapabilityResponse>> diagnostics(
            Authentication authentication
    ) {
        authenticatedUser(authentication);

        List<ProviderCapabilityResponse> response = providers.stream()
                .map(provider -> {
                    MerchantAcquiringProvider.Capabilities capabilities = provider.capabilities();
                    return new ProviderCapabilityResponse(
                            provider.code(),
                            capabilities.configured(),
                            capabilities.merchantOnboardingAvailable(),
                            capabilities.qrAcceptanceAvailable(),
                            capabilities.cardAcceptanceAvailable(),
                            capabilities.contactlessAvailable(),
                            capabilities.settlementAvailable(),
                            capabilities.reportingAvailable(),
                            capabilities.status()
                    );
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns a non-secret checklist for the next provider integration milestone.
     * A false readiness flag must never be bypassed by the mobile/POS clients.
     */
    @GetMapping("/provisioning-readiness")
    public ResponseEntity<InterswitchProvisioningReadiness.Readiness> provisioningReadiness(
            Authentication authentication
    ) {
        authenticatedUser(authentication);
        return ResponseEntity.ok(interswitchProvisioningReadiness.snapshot());
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }

    public record ProviderCapabilityResponse(
            String provider,
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
