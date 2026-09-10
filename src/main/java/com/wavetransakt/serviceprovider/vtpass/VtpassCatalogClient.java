package com.wavetransakt.serviceprovider.vtpass;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Category;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VtpassCatalogClient {

    private final RestTemplate restTemplate;

    @Value("${vtpass.base-url}")
    private String baseUrl;

    @Value("${vtpass.api-key:}")
    private String apiKey;

    @Value("${vtpass.public-key:}")
    private String publicKey;

    public List<Category> getCategories() {
        JsonNode root = get("service-categories", null, null);
        JsonNode content = root.path("content");

        List<Category> result = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode item : content) {
                result.add(new Category(
                        text(item, "identifier"),
                        text(item, "name")
                ));
            }
        }
        return result;
    }

    public List<Provider> getProviders(String identifier) {
        String safeIdentifier = safeToken(identifier, "identifier");
        JsonNode root = get("services", "identifier", safeIdentifier);
        JsonNode content = root.path("content");

        List<Provider> result = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode item : content) {
                result.add(new Provider(
                        text(item, "serviceID"),
                        text(item, "name"),
                        decimal(item, "minimium_amount", "minimum_amount"),
                        decimal(item, "maximum_amount"),
                        firstText(item, "convinience_fee", "convenience_fee"),
                        text(item, "product_type"),
                        text(item, "image")
                ));
            }
        }
        return result;
    }

    public VariationList getVariations(String serviceId) {
        String safeServiceId = safeToken(serviceId, "serviceId");
        JsonNode root = get("service-variations", "serviceID", safeServiceId);
        JsonNode content = root.path("content");
        JsonNode variations = content.path("variations");
        if (!variations.isArray()) {
            variations = content.path("varations");
        }

        List<Variation> result = new ArrayList<>();
        if (variations.isArray()) {
            for (JsonNode item : variations) {
                result.add(new Variation(
                        text(item, "variation_code"),
                        text(item, "name"),
                        decimal(item, "variation_amount"),
                        "yes".equalsIgnoreCase(text(item, "fixedPrice"))
                ));
            }
        }

        return new VariationList(
                firstText(content, "serviceID", "serviceId"),
                firstText(content, "ServiceName", "serviceName"),
                result
        );
    }

    private JsonNode get(String path, String queryName, String queryValue) {
        requireReadCredentials();

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(normalizedBaseUrl() + path);

        if (queryName != null && queryValue != null) {
            builder.queryParam(queryName, queryValue);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", apiKey.trim());
        headers.set("public-key", publicKey.trim());

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );

        JsonNode body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("VTpass returned an empty response");
        }

        String code = firstText(body, "response_description", "code");
        if (
                !code.isBlank() &&
                !"000".equals(code) &&
                !code.toUpperCase(Locale.ROOT).contains("SUCCESS")
        ) {
            throw new IllegalStateException(
                    "VTpass catalog request failed: " + code
            );
        }

        return body;
    }

    private void requireReadCredentials() {
        if (apiKey == null || apiKey.isBlank() || publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException(
                    "VTpass sandbox credentials are not configured on the server"
            );
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new IllegalStateException("VTpass base URL must use HTTPS");
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private String safeToken(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            String raw = text(node, field);
            if (!raw.isBlank()) {
                try {
                    return new BigDecimal(raw.replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // Try the next compatible field name.
                }
            }
        }
        return null;
    }
}
