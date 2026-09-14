package com.wavetransakt.serviceprovider.vtpass;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.serviceprovider.dto.ServiceAccountVerificationRequest;
import com.wavetransakt.serviceprovider.dto.ServiceAccountVerificationResponse;
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

import java.util.List;

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

    public ServiceAccountVerificationResponse verify(ServiceAccountVerificationRequest request) {
        validateConfigured();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("serviceID", request.serviceId().trim());
        form.add("billersCode", request.billersCode().trim());
        if (request.type() != null && !request.type().isBlank()) {
            form.add("type", request.type().trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", apiKey.trim());
        headers.set("secret-key", secretKey.trim());

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    normalizedBaseUrl() + "merchant-verify",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    JsonNode.class
            );
            return parse(request, response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Provider could not verify this customer account");
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Service provider verification is temporarily unavailable");
        }
    }

    private ServiceAccountVerificationResponse parse(
            ServiceAccountVerificationRequest request,
            JsonNode body
    ) {
        if (body == null) {
            throw new IllegalStateException("Service provider returned no verification response");
        }

        String code = text(body, "code");
        JsonNode content = body.path("content");
        boolean wrongBillersCode = content.path("WrongBillersCode").asBoolean(false);
        boolean verified = "000".equals(code) && !wrongBillersCode;

        String customerName = firstText(content,
                "Customer_Name", "customer_name", "name", "CustomerName");
        String address = firstText(content,
                "Address", "address", "Customer_Address");
        String accountType = firstText(content,
                "Meter_Type", "Customer_Account_Type", "current_bouquet", "Current_Bouquet");
        String message = firstText(body,
                "response_description", "responseDescription", "message");

        if (!verified) {
            return new ServiceAccountVerificationResponse(
                    false,
                    request.serviceId().trim(),
                    request.billersCode().trim(),
                    null,
                    null,
                    null,
                    message.isBlank() ? "Customer account could not be verified" : message
            );
        }

        return new ServiceAccountVerificationResponse(
                true,
                request.serviceId().trim(),
                request.billersCode().trim(),
                customerName.isBlank() ? null : customerName,
                address.isBlank() ? null : address,
                accountType.isBlank() ? null : accountType,
                message.isBlank() ? "Customer account verified" : message
        );
    }

    private void validateConfigured() {
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("VTpass verification credentials are not configured on the server");
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new IllegalStateException("VTpass base URL must use HTTPS");
        }
        return value.endsWith("/") ? value : value + "/";
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

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }
}
