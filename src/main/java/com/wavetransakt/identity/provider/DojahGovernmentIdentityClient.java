package com.wavetransakt.identity.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Server-side government identity lookup for Nigerian BVN and NIN.
 * Raw identifiers and provider identity payloads are never returned to Android.
 */
@Component
public class DojahGovernmentIdentityClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${wave.identity.liveness.dojah.base-url:https://api.dojah.io}")
    private String baseUrl;

    @Value("${wave.identity.liveness.dojah.app-id:}")
    private String appId;

    @Value("${wave.identity.liveness.dojah.api-key:}")
    private String apiKey;

    public DojahGovernmentIdentityClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    public IdentityRecord lookupBvn(String bvn) {
        String value = requireElevenDigits(bvn, "BVN");
        String raw = get("/api/v1/kyc/bvn/full", "bvn", value);
        return parseRecord(raw, "BVN");
    }

    public IdentityRecord lookupNin(String nin) {
        String value = requireElevenDigits(nin, "NIN");
        String raw = get("/api/v1/kyc/nin", "nin", value);
        return parseRecord(raw, "NIN");
    }

    private String get(String path, String queryName, String identifier) {
        if (!isConfigured()) {
            throw new IllegalStateException("Dojah government identity verification is not configured on the server");
        }

        try {
            String raw = restClient.get()
                    .uri(builder -> builder
                            .scheme(normalizedBaseUrl().startsWith("https://") ? "https" : "http")
                            .host(hostOnly())
                            .path(path)
                            .queryParam(queryName, identifier)
                            .build())
                    .header("AppId", appId.trim())
                    .header("Authorization", apiKey.trim())
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Identity provider returned an empty response");
            }
            return raw;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 404) {
                throw new IllegalArgumentException("The supplied government identity number could not be verified");
            }
            if (status == 401 || status == 403) {
                throw new IllegalStateException("Dojah identity credentials are not authorized for government lookup");
            }
            throw new IllegalStateException("Government identity verification is temporarily unavailable");
        }
    }

    private IdentityRecord parseRecord(String raw, String type) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode entity = root.path("entity");
            if (entity.isMissingNode() || entity.isNull() || !entity.isObject()) {
                throw new IllegalArgumentException(type + " could not be verified");
            }

            String firstName = text(entity, "first_name");
            String lastName = text(entity, "last_name");
            String middleName = text(entity, "middle_name");
            String gender = text(entity, "gender");
            LocalDate dob = parseDate(text(entity, "date_of_birth"));
            String reference = text(entity, "reference");

            if (firstName.isBlank() || lastName.isBlank() || dob == null) {
                throw new IllegalStateException("Identity provider response was incomplete");
            }

            return new IdentityRecord(firstName, lastName, middleName, gender, dob, reference);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read government identity verification response");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText("").trim() : "";
    }

    private String requireElevenDigits(String value, String label) {
        if (value == null || !value.matches("\\d{11}")) {
            throw new IllegalArgumentException(label + " must be exactly 11 digits");
        }
        return value;
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.dojah.io" : baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String hostOnly() {
        String value = normalizedBaseUrl();
        value = value.replaceFirst("^https?://", "");
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    public record IdentityRecord(
            String firstName,
            String lastName,
            String middleName,
            String gender,
            LocalDate dateOfBirth,
            String providerReference
    ) {}
}
