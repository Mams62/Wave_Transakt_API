package com.wavetransakt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaystackService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String secretKey;
    private final String baseUrl;

    public PaystackService(@Value("${paystack.secret-key}") String secretKey,
                           @Value("${paystack.base-url}") String baseUrl) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> initialize(String email, BigDecimal amountNaira, String reference) {
        requireConfigured();
        HttpHeaders headers = headers();
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("amount", amountNaira.movePointRight(2).longValueExact());
        body.put("reference", reference);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/transaction/initialize",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        return response.getBody();
    }

    public Map<String, Object> verify(String reference) {
        requireConfigured();
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/transaction/verify/" + reference,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class
        );
        return response.getBody();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void requireConfigured() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("PAYSTACK_SECRET_KEY is not configured");
        }
    }
}
