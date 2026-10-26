package com.wavetransakt.auth.controller;

import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Preflight for registration, before the phone submits BVN/NIN.
 *
 * This reports only whether provider configuration exists; it does not claim
 * that the provider credentials are authorized or that onboarding succeeded.
 * Never include secrets, identity numbers, or upstream error details.
 */
@RestController
@RequiredArgsConstructor
public class RegistrationAvailabilityController {

    private final DojahGovernmentIdentityClient identityProvider;

    @GetMapping("/api/auth/registration/availability")
    public ResponseEntity<Map<String, Object>> availability() {
        boolean configured = identityProvider.isConfigured();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "identityProviderConfigured", configured,
                        "registrationSubmissionAllowed", configured,
                        "message", configured
                                ? "Identity verification is configured. BVN and NIN will be verified before registration."
                                : "Account registration is temporarily unavailable while BVN/NIN verification is being connected. Please try again once verification is available."
                ));
    }
}
