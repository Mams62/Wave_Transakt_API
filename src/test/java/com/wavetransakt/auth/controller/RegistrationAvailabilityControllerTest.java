package com.wavetransakt.auth.controller;

import com.wavetransakt.identity.provider.GovernmentIdentityLookup;
import com.wavetransakt.identity.service.LivenessService;
import com.wavetransakt.verification.service.EmailOtpSender;
import com.wavetransakt.verification.service.SmsOtpSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationAvailabilityControllerTest {
    @Mock GovernmentIdentityLookup governmentIdentity;
    @Mock EmailOtpSender emailSender;
    @Mock SmsOtpSender smsSender;
    @Mock LivenessService liveness;

    private ResponseEntity<Map<String, Object>> readiness() {
        return new RegistrationAvailabilityController(
                governmentIdentity, emailSender, smsSender, liveness
        ).availability();
    }

    @Test
    void failsClosedWhenIdentityProviderMissing() {
        when(governmentIdentity.isConfigured()).thenReturn(false);
        ResponseEntity<Map<String, Object>> reply = readiness();
        assertEquals(200, reply.getStatusCode().value());
        assertEquals(Boolean.FALSE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.FALSE, reply.getBody().get("identityProviderConfigured"));
        assertTrue(reply.getHeaders().getCacheControl().contains("no-store"));
        assertFalse(reply.getBody().toString().contains("api-key"));
    }

    @Test
    void doesNotPermitRegistrationWhenEmailOrSmsDeliveryMissing() {
        when(governmentIdentity.isConfigured()).thenReturn(true);
        when(emailSender.isConfigured()).thenReturn(false);
        when(smsSender.isConfigured()).thenReturn(false);
        when(liveness.isReadyForEnrollment()).thenReturn(true);
        ResponseEntity<Map<String, Object>> reply = readiness();
        assertEquals(Boolean.FALSE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.FALSE, reply.getBody().get("emailDeliveryConfigured"));
        assertEquals(Boolean.FALSE, reply.getBody().get("smsDeliveryConfigured"));
    }

    @Test
    void doesNotPermitRegistrationWhenFaceEnrollmentUnavailable() {
        when(governmentIdentity.isConfigured()).thenReturn(true);
        when(emailSender.isConfigured()).thenReturn(true);
        when(smsSender.isConfigured()).thenReturn(true);
        when(liveness.isReadyForEnrollment()).thenReturn(false);
        assertEquals(Boolean.FALSE, readiness().getBody().get("registrationSubmissionAllowed"));
    }

    @Test
    void reportsConfigurationOnlyNeverClaimsCustomerVerified() {
        when(governmentIdentity.isConfigured()).thenReturn(true);
        when(emailSender.isConfigured()).thenReturn(true);
        when(smsSender.isConfigured()).thenReturn(true);
        when(liveness.isReadyForEnrollment()).thenReturn(true);
        ResponseEntity<Map<String, Object>> reply = readiness();
        assertEquals(Boolean.TRUE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.TRUE, reply.getBody().get("identityProviderConfigured"));
        assertEquals(Boolean.TRUE, reply.getBody().get("emailDeliveryConfigured"));
        assertEquals(Boolean.TRUE, reply.getBody().get("smsDeliveryConfigured"));
        assertEquals(Boolean.TRUE, reply.getBody().get("faceVerificationConfigured"));
        assertFalse(reply.getBody().containsKey("verificationResult"));
    }
}
