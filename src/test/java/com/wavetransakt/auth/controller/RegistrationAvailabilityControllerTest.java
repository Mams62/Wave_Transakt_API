package com.wavetransakt.auth.controller;

import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
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
    @Mock DojahGovernmentIdentityClient dojah;

    @Test
    void refusesRegistrationBeforeProviderIsConfigured() {
        when(dojah.isConfigured()).thenReturn(false);
        ResponseEntity<Map<String, Object>> reply =
                new RegistrationAvailabilityController(dojah).availability();
        assertEquals(200, reply.getStatusCode().value());
        assertEquals(Boolean.FALSE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.FALSE, reply.getBody().get("identityProviderConfigured"));
        assertTrue(reply.getHeaders().getCacheControl().contains("no-store"));
        assertFalse(reply.getBody().toString().contains("api-key"));
        verify(dojah, times(1)).isConfigured();
    }

    @Test
    void reportsOnlyConfigurationNotSuccessWhenKeysArePresent() {
        when(dojah.isConfigured()).thenReturn(true);
        ResponseEntity<Map<String, Object>> reply =
                new RegistrationAvailabilityController(dojah).availability();
        assertEquals(200, reply.getStatusCode().value());
        assertEquals(Boolean.TRUE, reply.getBody().get("registrationSubmissionAllowed"));
        assertEquals(Boolean.TRUE, reply.getBody().get("identityProviderConfigured"));
        assertFalse(reply.getBody().containsKey("verificationResult"));
    }
}
