package com.wavetransakt.identity.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class DojahLivenessClient {

    private static final int MAX_BASE64_CHARS = 8_000_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${wave.identity.liveness.dojah.base-url:https://api.dojah.io}")
    private String baseUrl;

    @Value("${wave.identity.liveness.dojah.app-id:}")
    private String appId;

    @Value("${wave.identity.liveness.dojah.api-key:}")
    private String apiKey;

    public DojahLivenessClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    public Result check(String imageBase64) {
        if (!isConfigured()) {
            throw new IllegalStateException("Dojah liveness credentials are not configured");
        }

        String image = normalizeImage(imageBase64);
        if (image.isBlank()) {
            throw new IllegalArgumentException("Selfie image is required");
        }
        if (image.length() > MAX_BASE64_CHARS) {
            throw new IllegalArgumentException("Selfie image is too large");
        }

        String raw = restClient.post()
                .uri(normalizedBaseUrl() + "/api/v1/ml/liveness")
                .contentType(MediaType.APPLICATION_JSON)
                .header("AppId", appId.trim())
                .header("Authorization", apiKey.trim())
                .body(Map.of("image", image))
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Dojah returned an empty liveness response");
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode liveness = root.path("entity").path("liveness");
            JsonNode face = root.path("entity").path("face");

            boolean livenessCheck = liveness.path("liveness_check").asBoolean(false);
            double probability = liveness.path("liveness_probability").asDouble(0.0);
            boolean faceDetected = face.path("face_detected").asBoolean(false);
            boolean multifaceDetected = face.path("multiface_detected").asBoolean(false);

            boolean passed = livenessCheck
                    && probability > 50.0
                    && faceDetected
                    && !multifaceDetected;

            return new Result(
                    passed,
                    probability,
                    faceDetected,
                    multifaceDetected
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read Dojah liveness response");
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank()
                ? "https://api.dojah.io"
                : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeImage(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int comma = trimmed.indexOf(',');
        if (trimmed.startsWith("data:image/") && comma >= 0) {
            return trimmed.substring(comma + 1).trim();
        }
        return trimmed;
    }

    public record Result(
            boolean passed,
            double probability,
            boolean faceDetected,
            boolean multifaceDetected
    ) {}
}
