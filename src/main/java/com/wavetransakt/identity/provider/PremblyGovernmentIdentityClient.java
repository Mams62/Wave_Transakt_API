package com.wavetransakt.identity.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Secure Prembly identity-only adapter. The API key never reaches Android.
 * Face/liveness enrollment is separate and MUST NOT be bypassed by this client.
 */
@Component
public class PremblyGovernmentIdentityClient implements GovernmentIdentityLookup {

    private final RestClient restClient;
    private final ObjectMapper mapper;

    @Value("${wave.identity.prembly.api-key:}")
    private String apiKey;

    public PremblyGovernmentIdentityClient(ObjectMapper mapper) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory transport = new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(4000);
        transport.setReadTimeout(8000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.prembly.com")
                .requestFactory(transport)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public IdentityRecord lookupBvn(String bvn) {
        return verify("BVN", "/verification/bvn", "number", bvn);
    }

    @Override
    public IdentityRecord lookupNin(String nin) {
        return verify("NIN", "/verification/vnin", "number_nin", nin);
    }

    private IdentityRecord verify(String type, String path, String field, String identifier) {
        if (identifier == null || !identifier.matches("[0-9]{11}")) {
            throw new IllegalArgumentException(type + " must contain exactly 11 digits");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("Approved identity verification is unavailable");
        }
        try {
            String response = restClient.post()
                    .uri(path)
                    .header("x-api-key", apiKey.trim())
                    .header("Accept", "application/json")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(field, identifier))
                    .retrieve()
                    .body(String.class);
            return parseResult(response, type);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Supplied identity details could not be verified");
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new IllegalStateException("Identity verification access is not authorized");
            }
            // Do not log request URLs, raw identifiers or provider response bodies.
            throw new IllegalStateException("Identity service temporarily unavailable");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Identity service temporarily unavailable");
        }
    }

    /** Package-private parser for non-network regression tests. */
    IdentityRecord parseResult(String json, String type) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.path("status").asBoolean(false)
                    || !"00".equals(root.path("response_code").asText(""))) {
                throw new IllegalArgumentException("Identity could not be verified");
            }
            JsonNode verification = root.path("verification");
            if (verification.isObject()) {
                String status = first(verification, "status");
                if (!status.isBlank() && !"VERIFIED".equalsIgnoreCase(status)) {
                    throw new IllegalArgumentException("Identity could not be verified");
                }
            }

            JsonNode data = root.path("data");
            if (!data.isObject()) {
                throw new IllegalArgumentException("Identity response is incomplete");
            }
            if (data.path("nin_suspension_status").asBoolean(false)) {
                throw new IllegalArgumentException("NIN verification was not accepted");
            }
            String firstName = first(data, "firstName", "firstname", "first_name");
            String lastName = first(data, "lastName", "surname", "last_name");
            String middleName = first(data, "middleName", "middlename", "middle_name");
            String gender = first(data, "gender", "sex");
            String rawDate = first(data, "dateOfBirth", "birthdate", "date_of_birth", "dob");
            LocalDate date = parseDate(rawDate);

            // Unlike a mere valid-number check, the account comparison
            // requires both names and actual date of birth.
            if (firstName.isBlank() || lastName.isBlank() || date == null) {
                throw new IllegalArgumentException("Identity response lacks required matching fields");
            }
            String reference = first(verification, "reference", "verification_id");
            return new IdentityRecord(firstName, lastName, middleName, gender, date, reference);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not validate identity response");
        }
    }

    private static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode item = node.path(field);
            if (item.isTextual() && !item.asText().isBlank()) {
                return item.asText().trim();
            }
        }
        return "";
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (DateTimeFormatter pattern : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
        )) {
            try { return LocalDate.parse(raw.trim(), pattern); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
