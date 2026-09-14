package com.wavetransakt.serviceprovider.vtpass;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VtpassPurchaseClient {

    private final RestTemplate restTemplate;

    @Value("${vtpass.base-url}")
    private String baseUrl;

    @Value("${vtpass.api-key:}")
    private String apiKey;

    @Value("${vtpass.secret-key:}")
    private String secretKey;

    public void validateConfigured() {
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "VTpass sandbox purchase credentials are not configured on the server"
            );
        }
    }

    public ProviderResult purchase(
            String serviceKind,
            String serviceId,
            String variationCode,
            BigDecimal amount,
            String recipient,
            String customerPhone,
            String serviceOption,
            String providerRequestId
    ) {
        validateConfigured();
        String kind = serviceKind == null ? "" : serviceKind.trim().toUpperCase(Locale.ROOT);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("request_id", providerRequestId);
        form.add("serviceID", serviceId);
        form.add("amount", amount.toPlainString());

        switch (kind) {
            case "AIRTIME" -> form.add("phone", recipient);
            case "DATA" -> {
                form.add("phone", recipient);
                form.add("billersCode", recipient);
                form.add("variation_code", requireValue(variationCode, "Data variation code"));
            }
            case "ELECTRICITY" -> {
                form.add("phone", requireValue(customerPhone, "Customer phone"));
                form.add("billersCode", recipient);
                form.add("variation_code", requireMeterType(serviceOption));
            }
            case "TV" -> {
                form.add("phone", requireValue(customerPhone, "Customer phone"));
                form.add("billersCode", recipient);
                form.add("variation_code", requireValue(variationCode, "TV bouquet variation code"));
                // DSTV/GOTV require subscription_type for bouquet changes. Startimes
                // does not require it, so omit the field for that provider.
                if (!"startimes".equalsIgnoreCase(serviceId)) {
                    form.add("subscription_type", requireTvOption(serviceOption));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported provider service kind");
        }

        return post("pay", form);
    }

    public ProviderResult requery(String providerRequestId) {
        validateConfigured();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("request_id", providerRequestId);
        return post("requery", form);
    }

    private ProviderResult post(
            String path,
            MultiValueMap<String, String> form
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", apiKey.trim());
        headers.set("secret-key", secretKey.trim());

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    normalizedBaseUrl() + path,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    JsonNode.class
            );
            return parse(response.getBody());
        } catch (ResourceAccessException ex) {
            return ProviderResult.pending(
                    "PROVIDER_TIMEOUT",
                    null,
                    "Provider response was unavailable; status must be requeried"
            );
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                return ProviderResult.failed(
                        "HTTP_" + ex.getStatusCode().value(),
                        null,
                        "Provider rejected the service request"
                );
            }
            return ProviderResult.pending(
                    "HTTP_" + ex.getStatusCode().value(),
                    null,
                    "Provider response is uncertain; status must be requeried"
            );
        }
    }

    private ProviderResult parse(JsonNode body) {
        if (body == null) {
            return ProviderResult.pending(
                    "EMPTY_RESPONSE",
                    null,
                    "Provider returned no readable response; status must be requeried"
            );
        }

        String code = text(body, "code");
        String description = text(body, "response_description");
        JsonNode transaction = body.path("content").path("transactions");
        String transactionStatus = text(transaction, "status");
        String transactionId = text(transaction, "transactionId");

        String normalizedStatus = transactionStatus.toLowerCase(Locale.ROOT);
        String normalizedDescription = description.toUpperCase(Locale.ROOT);

        if (normalizedStatus.equals("delivered") ||
                normalizedStatus.equals("successful") ||
                normalizedStatus.equals("success")) {
            return ProviderResult.success(
                    transactionStatus,
                    transactionId,
                    description.isBlank() ? "Service delivered" : description
            );
        }

        if (normalizedStatus.equals("failed") ||
                normalizedStatus.equals("reversed") ||
                normalizedStatus.equals("cancelled")) {
            return ProviderResult.failed(
                    transactionStatus,
                    transactionId,
                    description.isBlank() ? "Provider reported failure" : description
            );
        }

        if ("099".equals(code) ||
                normalizedStatus.equals("pending") ||
                normalizedStatus.equals("initiated") ||
                normalizedStatus.equals("processing")) {
            return ProviderResult.pending(
                    transactionStatus.isBlank() ? code : transactionStatus,
                    transactionId,
                    description.isBlank() ? "Provider is processing the request" : description
            );
        }

        if (normalizedDescription.contains("FAILED") ||
                normalizedDescription.contains("INVALID") ||
                normalizedDescription.contains("DOES NOT EXIST") ||
                normalizedDescription.contains("INSUFFICIENT") ||
                normalizedDescription.contains("LOW BALANCE") ||
                normalizedDescription.contains("NOT AVAILABLE")) {
            return ProviderResult.failed(
                    transactionStatus.isBlank() ? code : transactionStatus,
                    transactionId,
                    description
            );
        }

        // VTpass recommends treating any unclear or unexpected response as pending
        // and confirming it through the requery endpoint.
        return ProviderResult.pending(
                transactionStatus.isBlank() ? code : transactionStatus,
                transactionId,
                description.isBlank()
                        ? "Provider outcome is uncertain; status must be requeried"
                        : description
        );
    }

    private String requireValue(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private String requireMeterType(String value) {
        String option = requireValue(value, "Electricity meter type").toLowerCase(Locale.ROOT);
        if (!option.equals("prepaid") && !option.equals("postpaid")) {
            throw new IllegalArgumentException("Electricity meter type must be prepaid or postpaid");
        }
        return option;
    }

    private String requireTvOption(String value) {
        String option = requireValue(value, "TV subscription option").toLowerCase(Locale.ROOT);
        if (!option.equals("change") && !option.equals("renew")) {
            throw new IllegalArgumentException("TV subscription option must be change or renew");
        }
        return option;
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new IllegalStateException("VTpass base URL must use HTTPS");
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull()
                ? ""
                : value.asText("").trim();
    }

    public enum ProviderOutcome {
        SUCCESS,
        PENDING,
        FAILED
    }

    public record ProviderResult(
            ProviderOutcome outcome,
            String providerStatus,
            String transactionId,
            String message
    ) {
        public static ProviderResult success(
                String status,
                String transactionId,
                String message
        ) {
            return new ProviderResult(ProviderOutcome.SUCCESS, status, transactionId, message);
        }

        public static ProviderResult pending(
                String status,
                String transactionId,
                String message
        ) {
            return new ProviderResult(ProviderOutcome.PENDING, status, transactionId, message);
        }

        public static ProviderResult failed(
                String status,
                String transactionId,
                String message
        ) {
            return new ProviderResult(ProviderOutcome.FAILED, status, transactionId, message);
        }
    }
}
