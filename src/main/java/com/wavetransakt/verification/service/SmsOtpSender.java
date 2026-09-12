package com.wavetransakt.verification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends Wave security OTPs through a Termii-compatible SMS endpoint.
 * Credentials live only in the server environment. If the provider is not
 * configured this service reports "not configured" so controlled staging can
 * expose generated codes only when WAVE_RETURN_VERIFICATION_CODE=true.
 */
@Service
@RequiredArgsConstructor
public class SmsOtpSender {

    private final RestTemplate restTemplate;

    @Value("${wave.sms.base-url:}")
    private String baseUrl;

    @Value("${wave.sms.api-key:}")
    private String apiKey;

    @Value("${wave.sms.sender-id:WavePay}")
    private String senderId;

    @Value("${wave.sms.channel:dnd}")
    private String channel;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && senderId != null && !senderId.isBlank();
    }

    public boolean sendLoginOtp(String phoneNumber, String code) {
        return sendOtp(
                phoneNumber,
                code,
                "Your Wave Transakt login code is " + code + ". It expires in 10 minutes. Do not share this code.",
                "login"
        );
    }

    public boolean sendAccountRecoveryOtp(String phoneNumber, String code) {
        return sendOtp(
                phoneNumber,
                code,
                "Your Wave Transakt account PIN recovery code is " + code + ". It expires in 15 minutes. Do not share this code.",
                "account recovery"
        );
    }

    private boolean sendOtp(String phoneNumber, String code, String message, String purpose) {
        if (!isConfigured()) {
            return false;
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Security OTP must be 6 digits");
        }

        String to = normalizeNigerianPhone(phoneNumber);
        String endpoint = stripTrailingSlash(baseUrl) + "/api/sms/send";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey.trim());
        body.put("to", to);
        body.put("from", senderId.trim());
        body.put("sms", message);
        body.put("type", "plain");
        body.put("channel", channel == null || channel.isBlank() ? "dnd" : channel.trim());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return true;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Unable to deliver the " + purpose + " code to the registered phone number",
                    e
            );
        }
    }

    private String normalizeNigerianPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Registered phone number is required");
        }

        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = "234" + digits.substring(1);
        }
        if (!digits.matches("234\\d{10}")) {
            throw new IllegalArgumentException(
                    "Registered phone number must be a valid Nigerian mobile number"
            );
        }
        return digits;
    }

    private String stripTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
