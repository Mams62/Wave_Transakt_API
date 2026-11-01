package com.wavetransakt.identity.smile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Smile ID V3 Enhanced KYC submission client, restricted to their sandbox.
 *
 * An HTTP 202 merely means a job was accepted. This component NEVER approves
 * customer KYC, changes user state, creates a wallet, or enables payments.
 * Only approved, authenticated, replay-safe final results from a future
 * callback orchestrator may advance KYC after all required checks pass.
 *
 * Never put API tokens, BVN/NIN, response bodies, or face images in logs.
 */
@Component
public final class SmileIdEnhancedKycClient {
    private static final String SANDBOX_URL = "https://api.sandbox.smileidentity.com";

    private final RestClient client;
    private final ObjectMapper mapper;
    private final boolean sandboxEnabled;
    private final String partnerId;
    private final String apiKey;
    private final String callbackUrl;
    private final String privacyPolicyUrl;

    public SmileIdEnhancedKycClient(
            ObjectMapper mapper,
            @Value("${wave.identity.smile-id.sandbox-enabled:false}") boolean sandboxEnabled,
            @Value("${wave.identity.smile-id.partner-id:}") String partnerId,
            @Value("${wave.identity.smile-id.api-key:}") String apiKey,
            @Value("${wave.identity.smile-id.callback-url:}") String callbackUrl,
            @Value("${wave.identity.smile-id.privacy-policy-url:}") String privacyPolicyUrl) {
        this.mapper = mapper;
        this.sandboxEnabled = sandboxEnabled;
        this.partnerId = partnerId == null ? "" : partnerId.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.callbackUrl = callbackUrl == null ? "" : callbackUrl.trim();
        this.privacyPolicyUrl = privacyPolicyUrl == null ? "" : privacyPolicyUrl.trim();

        SimpleClientHttpRequestFactory transport = new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(4000);
        transport.setReadTimeout(10000);
        this.client = RestClient.builder().baseUrl(SANDBOX_URL)
                .requestFactory(transport).build();
    }

    public boolean isConfiguredForSandbox() {
        return sandboxEnabled && partnerId.matches("[1-9][0-9]*")
                && !apiKey.isBlank()
                && isHttpsUrl(callbackUrl)
                && isHttpsUrl(privacyPolicyUrl);
    }

    /**
     * Accepts only Nigerian BVN or NIN sandbox verification jobs.
     * Caller MUST persist idempotency/attempt records before submission.
     * Caller MUST ensure authorized consent and provider entitlement.
     */
    public PendingJob submitEnhancedKyc(VerificationInput input) {
        if (!isConfiguredForSandbox()) {
            throw new IllegalStateException("Smile ID sandbox onboarding is not configured");
        }
        validate(input);

        String userId = input.userId().toString();
        try {
            JsonNode tokenBody = client.post().uri("/v3/token")
                    .header("smileid-partner-id", partnerId)
                    .header("smileid-api-key", apiKey)
                    .retrieve().body(JsonNode.class);
            String accessToken = tokenBody == null ? "" : tokenBody.path("token").asText("");
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Identity provider authentication unavailable");
            }

            Map<String, Object> person = new LinkedHashMap<>();
            person.put("given_names", input.givenNames().trim());
            person.put("last_name", input.lastName().trim());
            person.put("email", input.email().trim());
            if (input.phoneE164() != null && !input.phoneE164().isBlank()) {
                person.put("phone_number", input.phoneE164().trim());
            }

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("country", "NG");
            form.add("id_type", input.idType().trim().toUpperCase(Locale.ROOT));
            form.add("id_number", input.idNumber());
            form.add("user_details", mapper.writeValueAsString(person));
            form.add("consent", mapper.writeValueAsString(Map.of(
                    "granted", true,
                    "granted_at", input.consentGrantedAt().toString(),
                    "notice_language", "EN",
                    "notice_privacy_policy_url", privacyPolicyUrl
            )));
            form.add("callback_url", callbackUrl);
            form.add("partner_params", mapper.writeValueAsString(
                    Map.of("wave_kyc_attempt_id", input.attemptId().toString())));

            ResponseEntity<JsonNode> response = client.post()
                    .uri("/v3/enhanced_kyc")
                    .header("SmileID-Partner-ID", partnerId)
                    .header("SmileID-Token", accessToken)
                    .header("User-ID", userId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve().toEntity(JsonNode.class);

            if (response.getStatusCode().value() != 202) {
                throw new IllegalStateException("Identity verification job was not accepted");
            }
            return parseAccepted(response.getBody(), userId);
        } catch (Exception e) {
            // Deliberately omit cause: HTTP failures can contain raw provider
            // response bodies and sensitive identity information.
            throw new IllegalStateException(
                    "Identity verification service unavailable or job not accepted");
        }
    }

    static PendingJob parseAccepted(JsonNode body, String expectedUserId) {
        if (body == null || !"Accepted".equals(body.path("status").asText(""))) {
            throw new IllegalStateException("Identity verification job not accepted");
        }
        String jobId = body.path("job_id").asText("");
        String userId = body.path("user_id").asText("");
        if (jobId.isBlank() || !expectedUserId.equals(userId)) {
            throw new IllegalStateException("Identity verification job response incomplete");
        }
        return new PendingJob(jobId, userId, "PENDING");
    }

    static void validate(VerificationInput input) {
        if (input == null || input.userId() == null || input.attemptId() == null
                || !input.consentGranted() || input.consentGrantedAt() == null
                || input.consentGrantedAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Valid user consent and attempt reference are required");
        }
        String idType = input.idType() == null ? "" : input.idType().trim().toUpperCase(Locale.ROOT);
        if (!idType.equals("BVN") && !idType.equals("NIN")) {
            throw new IllegalArgumentException("Unsupported government identity type");
        }
        if (input.idNumber() == null || !input.idNumber().matches("[0-9]{11}")) {
            throw new IllegalArgumentException("Government identity number must be 11 digits");
        }
        if (input.givenNames() == null || input.givenNames().isBlank()
                || input.lastName() == null || input.lastName().isBlank()
                || input.email() == null || !input.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Valid identity profile and email are required");
        }
        if (input.phoneE164() != null && !input.phoneE164().isBlank()
                && !input.phoneE164().matches("^\\+[1-9][0-9]{6,14}$")) {
            throw new IllegalArgumentException("Invalid international phone format");
        }
    }

    private static boolean isHttpsUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record VerificationInput(
            UUID userId, UUID attemptId, String idType, String idNumber,
            String givenNames, String lastName, String email, String phoneE164,
            boolean consentGranted, Instant consentGrantedAt) {}

    /** Acceptance is NEVER proof of successful KYC. */
    public record PendingJob(String jobId, String userId, String status) {}
}
