package com.wavetransakt.auth.controller;

import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
import com.wavetransakt.verification.service.EmailOtpSender;
import com.wavetransakt.verification.service.SmsOtpSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationAvailabilityControllerTest {
    @Mock DojahGovernmentIdentityClient dojah;
    @Mock EmailOtpSender email;
    @Mock SmsOtpSender sms;

    private RegistrationAvailabilityController controller(boolean liveness) {
        RegistrationAvailabilityController controller =
                new RegistrationAvailabilityController(dojah, email, sms);
        ReflectionTestUtils.setField(controller, "livenessConfigured", liveness);
        return controller;
    }

    @Test
    void refusesRegistrationWhenDojahIsMissing() {
        ResponseEntity<Map<String, Object>> reply = controller(true).availability();
        assertEquals(200, reply.getStatusCode().value());
        assertEquals(Boolean.FALSE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.FALSE, reply.getBody().get("identityProviderConfigured"));
        assertTrue(reply.getHeaders().getCacheControl().contains("no-store"));
        assertFalse(reply.getBody().toString().contains("api-key"));
    }

    @Test
    void requiresEmailSmsAndLivenessConfigurationToo() {
        when(dojah.isConfigured()).thenReturn(true);
        when(email.isConfigured()).thenReturn(true);
        when(sms.isConfigured()).thenReturn(false);
        ResponseEntity<Map<String, Object>> reply = controller(false).availability();
        assertEquals(Boolean.FALSE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.TRUE, reply.getBody().get("identityProviderConfigured"));
        assertEquals(Boolean.TRUE, reply.getBody().get("emailDeliveryConfigured"));
        assertEquals(Boolean.FALSE, reply.getBody().get("smsDeliveryConfigured"));
        assertEquals(Boolean.FALSE, reply.getBody().get("faceVerificationConfigured"));
    }

    @Test
    void configuredDependenciesStillDoNotClaimVerificationSucceeded() {
        when(dojah.isConfigured()).thenReturn(true);
        when(email.isConfigured()).thenReturn(true);
        when(sms.isConfigured()).thenReturn(true);
        ResponseEntity<Map<String, Object>> reply = controller(true).availability();
        assertEquals(Boolean.TRUE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.TRUE, reply.getBody().get("faceVerificationConfigured"));
        assertFalse(reply.getBody().containsKey("verificationResult"));
        assertFalse(reply.getBody().containsKey("token"));
    }
}
