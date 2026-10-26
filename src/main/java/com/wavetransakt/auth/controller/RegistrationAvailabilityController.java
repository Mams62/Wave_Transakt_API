package com.wavetransakt.auth.controller;

import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
import com.wavetransakt.verification.service.EmailOtpSender;
import com.wavetransakt.verification.service.SmsOtpSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Returns only the configuration state of dependencies required by onboarding.
 * It is not a statement of live provider authorization or email/SMS delivery.
 */
@RestController
@RequiredArgsConstructor
public class RegistrationAvailabilityController {

    private final DojahGovernmentIdentityClient identityProvider;
    private final EmailOtpSender emailOtpSender;
    private final SmsOtpSender smsOtpSender;

    @Value("${wave.identity.liveness.provider-configured:false}")
    private boolean livenessConfigured;

    @GetMapping("/api/auth/registration/availability")
    public ResponseEntity<Map<String, Object>> availability() {
        boolean identity = identityProvider.isConfigured();
        boolean email = emailOtpSender.isConfigured();
        boolean sms = smsOtpSender.isConfigured();
        boolean face = livenessConfigured && identity;
        boolean ready = identity && email && sms && face;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "identityProviderConfigured", identity,
                        "emailDeliveryConfigured", email,
                        "smsDeliveryConfigured", sms,
                        "faceVerificationConfigured", face,
                        "registrationSubmissionAllowed", ready,
                        "message", ready
                                ? "Registration dependencies are configured. Identity, email, phone and live-face checks still must succeed."
                                : "Secure account registration is paused until government identity, email, SMS and face verification providers are configured."
                ));
    }
}
