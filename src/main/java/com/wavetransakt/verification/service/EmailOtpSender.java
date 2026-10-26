package com.wavetransakt.verification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Transactional-email adapter. Sends server-generated verification codes through
 * a verified Resend sender domain. No secret or OTP appears in responses or logs.
 *
 * Provider credentials are optional at boot but mandatory when sending.
 * A missing or failing provider MUST block the account-verification workflow.
 */
@Service
@RequiredArgsConstructor
public class EmailOtpSender {
    private static final String ENDPOINT = "https://api.resend.com/emails";
    private final RestTemplate restTemplate;

    @Value("${wave.email.api-key:}")
    private String apiKey;

    @Value("${wave.email.from:}")
    private String fromAddress;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && fromAddress != null && !fromAddress.isBlank();
    }

    public void sendVerificationCode(String email, String code) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Email verification delivery is not configured. Account registration is unavailable."
            );
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid recipient email is required");
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Verification code must be six digits");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "from", fromAddress.trim(),
                "to", List.of(email.trim()),
                "subject", "Verify your Wave Transakt account",
                "text", "Your Wave Transakt email verification code is " + code
                        + ". It expires in 10 minutes. Never share this code."
        );
        try {
            ResponseEntity<String> result = restTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(payload, headers),
                    String.class
            );
            if (!result.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Email verification delivery is unavailable");
            }
        } catch (Exception ignored) {
            // Do not propagate provider responses: they can include credentials,
            // recipient identifiers or personally identifying data.
            throw new IllegalStateException(
                    "Email verification delivery is temporarily unavailable."
            );
        }
    }
}
