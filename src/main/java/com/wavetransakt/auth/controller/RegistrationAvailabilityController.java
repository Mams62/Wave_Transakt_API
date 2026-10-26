package com.wavetransakt.auth.controller;

import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
import com.wavetransakt.identity.service.LivenessService;
import com.wavetransakt.verification.service.EmailOtpSender;
import com.wavetransakt.verification.service.SmsOtpSender;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Zero-PII account preflight. All steps needed to finish setup must be
 * configured before asking a customer for BVN/NIN. A configured provider is
 * NOT a substitute for verified consent, active product entitlement, or a
 * successful provider response on the actual registration request.
 */
@RestController
@RequiredArgsConstructor
public class RegistrationAvailabilityController {
    private final DojahGovernmentIdentityClient governmentIdentity;
    private final EmailOtpSender emailSender;
    private final SmsOtpSender smsSender;
    private final LivenessService liveness;

    @GetMapping("/api/auth/registration/availability")
    public ResponseEntity<Map<String, Object>> availability() {
        boolean identityReady = governmentIdentity.isConfigured();
        boolean emailReady = emailSender.isConfigured();
        boolean smsReady = smsSender.isConfigured();
        boolean faceReady = liveness.isReadyForEnrollment();
        boolean ready = identityReady && emailReady && smsReady && faceReady;

        String message = ready
                ? "Account services are configured. Identity, email, phone and face checks are still required."
                : "Account setup is temporarily unavailable. " +
                  "Wave Transakt must connect BVN/NIN, email, phone OTP and face verification " +
                  "before accepting identity details.";

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "identityProviderConfigured", identityReady,
                        "emailDeliveryConfigured", emailReady,
                        "smsDeliveryConfigured", smsReady,
                        "faceVerificationConfigured", faceReady,
                        "registrationSubmissionAllowed", ready,
                        "message", message
                ));
    }
}
