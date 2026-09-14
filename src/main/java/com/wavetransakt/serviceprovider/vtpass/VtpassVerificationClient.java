package com.wavetransakt.serviceprovider.vtpass;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyResponse;
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
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VtpassVerificationClient {

    private final RestTemplate restTemplate;

    @Value("${vtpass.base-url}")
    private String baseUrl;

    @Value("${vtpass.api-key:}")
    private String apiKey;

    @Value("${vtpass.secret-key:}")
    private String secretKey;

    public VerifyResponse verify(
            String serviceKind,
            String serviceId,
            String customerReference,
            String option
    ) {
        validateConfigured();
        String kind = normalizeKind(serviceKind);
        String safeServiceId = safeToken(serviceId, "service ID");
        String safeReference = safeReference(customerReference);
        String normalizedOption = normalizeOption(kind, option);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("billersCode", safeReference);
        form.add("serviceID", safeServiceId);
        if ("ELECTRICITY".equals(kind)) {
            form.add("type", normalizedOption);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", apiKey.trim());
        headers.set("secret-key", secretKey.trim());

        JsonNode body;
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    normalizedBaseUrl() + "merchant-verify",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    JsonNode.class
            );
            body = response.getBody();
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Service provider verification timed out; try again before paying");
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Service provider rejected customer verification");
        }

        if (body == null) {
            throw new IllegalStateException("Service provider returned no verification response");
        }

        String code = text(body, "code");
        JsonNode content = body.path("content");
        boolean wrongReference = booleanValue(content, "WrongBillersCode");
        boolean valid = "000".equals(code) && !wrongReference;

        String customerName = firstText(content, "Customer_Name", "customer_name", "name");
        String address = firstText(content, "Address", "address");
        String accountType = firstText(
                content,
                "Meter_Type",
                "Customer_Account_Type",
                "Account_Type"
        );
        String currentPackage = firstText(content, "Current_Bouquet", "current_bouquet", "Bouquet");
        BigDecimal renewalAmount = decimal(content, "Renewal_Amount", "renewal_amount");
        BigDecimal minimumAmount = decimal(
                content,
                "Min_Purchase_Amount",
                "Minimum_Amount",
                "minimum_amount"
        );

        String message = valid
                ? "Customer verified"
                : firstText(body, "response_description", "message");
        if (!valid && message.isBlank()) {
            message = "The customer reference could not be verified";
        }

        return new VerifyResponse(
                "VTPASS",
                kind,
                safeServiceId,
                safeReference,
                customerName,
                address,
                accountType,
                currentPackage,
                renewalAmount,
                minimumAmount,
                valid,
                message
        );
    }

    private void validateConfigured() {
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("VTpass verification credentials are not configured on the server");
        }
    }

    private String normalizeKind(String value) {
        String kind = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!kind.equals("ELECTRICITY") && !kind.equals("TV")) {
            throw new IllegalArgumentException("Only ELECTRICITY and TV require this verification flow");
        }
        return kind;
    }

    private String normalizeOption(String kind, String value) {
        if (!"ELECTRICITY".equals(kind)) {
            return null;
        }
        String option = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!option.equals("prepaid") && !option.equals("postpaid")) {
            throw new IllegalArgumentException("Electricity meter type must be prepaid or postpaid");
        }
        return option;
    }

    private String safeToken(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private String safeReference(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{5,40}")) {
            throw new IllegalArgumentException("Invalid customer reference");
        }
        return normalized;
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new IllegalStateException("VTpass base URL must use HTTPS");
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) return false;
        if (value.isBoolean()) return value.asBoolean();
        return "true".equalsIgnoreCase(value.asText(""));
    }

    private BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            String raw = text(node, field);
            if (!raw.isBlank()) {
                try {
                    return new BigDecimal(raw.replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // Try another provider field alias.
                }
            }
        }
        return null;
    }
}
