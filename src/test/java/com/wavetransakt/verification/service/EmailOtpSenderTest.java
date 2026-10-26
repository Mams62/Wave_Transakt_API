package com.wavetransakt.verification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpSenderTest {
    @Mock RestTemplate http;

    private EmailOtpSender sender(String key, String from) {
        EmailOtpSender sender = new EmailOtpSender(http);
        ReflectionTestUtils.setField(sender, "apiKey", key);
        ReflectionTestUtils.setField(sender, "fromAddress", from);
        return sender;
    }

    @Test
    void missingConfigurationFailsClosedAndDoesNotContactExternalApi() {
        EmailOtpSender sender = sender("", "");
        assertFalse(sender.isConfigured());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("person@example.com", "456789"));
        assertTrue(error.getMessage().contains("not configured"));
        verifyNoInteractions(http);
    }

    @Test
    void configuredSenderPostsCodeWithServerSideBearerAuthorization() {
        EmailOtpSender sender = sender("test-credential", "Wave <verification@example.com>");
        when(http.exchange(eq("https://api.resend.com/emails"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"id\":\"abc\"}"));

        sender.sendVerificationCode("recipient@example.com", "456789");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String,Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).exchange(eq("https://api.resend.com/emails"), eq(HttpMethod.POST),
                captor.capture(), eq(String.class));
        assertEquals("Bearer test-credential",
                captor.getValue().getHeaders().getFirst("Authorization"));
        assertEquals("Wave <verification@example.com>",
                captor.getValue().getBody().get("from"));
        assertFalse(captor.getValue().getBody().containsKey("password"));
    }

    @Test
    void aProviderFailureNeverLeaksInternalResponseToCustomer() {
        EmailOtpSender sender = sender("test-credential", "verification@example.com");
        when(http.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(503).body("private provider diagnostic"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("recipient@example.com", "456789"));
        assertFalse(error.getMessage().contains("private provider diagnostic"));
    }
}
