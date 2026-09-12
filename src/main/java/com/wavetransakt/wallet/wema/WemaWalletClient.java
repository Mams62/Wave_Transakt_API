package com.wavetransakt.wallet.wema;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WemaWalletClient {

    private static final String SUBSCRIPTION_HEADER = "Ocp-Apim-Subscription-Key";
    private static final String API_KEY_HEADER = "x-api-key";

    private final RestTemplate restTemplate;

    @Value("${wema.wallet.base-url:https://playground.azure-api.net}")
    private String baseUrl;

    @Value("${wema.wallet.subscription-key:}")
    private String subscriptionKey;

    @Value("${wema.wallet.api-key:}")
    private String apiKey;

    public StartResult startNinWallet(User user) {
        requireCredentials();

        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        if (user.getNin() == null || !user.getNin().matches("\\d{11}")) {
            throw new IllegalArgumentException(
                    "A valid 11-digit NIN is required for Wema wallet creation"
            );
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new IllegalArgumentException(
                    "Phone number is required for Wema wallet creation"
            );
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required for Wema wallet creation"
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", user.getPhone().trim());
        body.put("email", user.getEmail().trim());
        body.put("nin", user.getNin().trim());

        JsonNode response = post(
                "/wallet-creation/api/CustomerAccount/GenerateWalletAccountForPartnerships/Request",
                body
        );

        assertSuccessful(
                response,
                "Wema wallet creation request was rejected"
        );

        String trackingId = firstExpectedText(
                response,
                "trackingId",
                "trackingID",
                "tracking_id"
        );

        if (trackingId.isBlank()) {
            throw new WemaProviderException(
                    "WEMA_TRACKING_ID_MISSING",
                    "Wema accepted the wallet request but did not return the tracking ID required for OTP validation. Confirm that the Wallet Creation API is enabled for this channel.",
                    null
            );
        }

        return new StartResult(
                trackingId,
                message(response, "Wema OTP requested successfully")
        );
    }

    public ProviderMessage validateNinOtp(
            String phoneNumber,
            String trackingId,
            String otp
    ) {
        requireCredentials();

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (trackingId == null || trackingId.isBlank()) {
            throw new IllegalArgumentException("Wema tracking ID is required");
        }
        if (otp == null || !otp.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("Invalid Wema OTP");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", phoneNumber.trim());
        body.put("otp", otp.trim());
        body.put("trackingId", trackingId.trim());

        JsonNode response = post(
                "/wallet-creation/api/CustomerAccount/GenerateWalletAccountForPartnershipsV2/otp",
                body
        );

        assertSuccessful(response, "Wema OTP validation failed");

        return new ProviderMessage(
                message(
                        response,
                        "OTP accepted. Wema wallet creation is pending."
                )
        );
    }

    public PartnershipAccountDetails getPartnershipAccountDetails(
            String phoneNumber
    ) {
        requireCredentials();

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(
                        normalizedBaseUrl() +
                                "/wallet-creation/api/CustomerAccount/GetPartnershipAccountDetails"
                )
                .queryParam("phoneNumber", phoneNumber.trim())
                .build(true)
                .toUriString();

        JsonNode response = exchange(url, HttpMethod.GET, null);
        assertSuccessful(
                response,
                "Unable to retrieve Wema wallet account details"
        );

        JsonNode data = response.path("data");
        String accountNumber = text(data, "accountNumber");

        if (accountNumber.isBlank()) {
            JsonNode result = response.path("result");
            accountNumber = text(result, "accountNumber");
        }
        if (accountNumber.isBlank()) {
            accountNumber = text(response, "accountNumber");
        }
        if (accountNumber.isBlank()) {
            accountNumber = firstRecursiveText(
                    response,
                    "accountNumber",
                    "nuban",
                    "NUBAN"
            );
        }

        return new PartnershipAccountDetails(
                accountNumber,
                message(
                        response,
                        accountNumber.isBlank()
                                ? "Wema wallet is still pending"
                                : "Wema wallet account created"
                )
        );
    }

    public WalletAccountDetails getWalletDetails(String accountNumber) {
        requireCredentials();

        if (accountNumber == null || !accountNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException(
                    "A valid 10-digit Wema account number is required"
            );
        }

        String url = normalizedBaseUrl() +
                "/ws-acct-mgt/api/AccountMaintenance/CustomerAccount/GetAccountV2/accountNumber/" +
                accountNumber;

        JsonNode response = exchange(url, HttpMethod.GET, null);
        assertSuccessful(response, "Unable to retrieve Wema wallet balance");

        JsonNode payload = response.path("result");
        if (!payload.isObject()) {
            payload = response.path("data");
        }
        if (!payload.isObject()) {
            payload = response;
        }

        String walletNumber = text(payload, "walletNumber");
        if (walletNumber.isBlank()) {
            walletNumber = text(payload, "accountNumber");
        }

        String balanceRaw = text(payload, "availableBalance");
        if (balanceRaw.isBlank()) {
            balanceRaw = text(payload, "balance");
        }

        String walletStatus = text(payload, "walletStatus");

        if (balanceRaw.isBlank()) {
            throw new WemaProviderException(
                    "WEMA_BALANCE_MISSING",
                    "Wema returned wallet details without an available balance.",
                    null
            );
        }

        BigDecimal availableBalance;
        try {
            availableBalance = new BigDecimal(
                    balanceRaw.replace(",", "").trim()
            );
        } catch (Exception e) {
            throw new WemaProviderException(
                    "WEMA_BALANCE_INVALID",
                    "Wema returned an invalid wallet balance.",
                    null,
                    e
            );
        }

        return new WalletAccountDetails(
                walletNumber.isBlank() ? accountNumber : walletNumber,
                availableBalance,
                walletStatus
        );
    }

    public Diagnostics diagnostics() {
        String configuredBase = baseUrl == null ? "" : baseUrl.trim();
        String host = "";
        try {
            java.net.URI uri = java.net.URI.create(configuredBase);
            host = uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ignored) {
        }

        boolean httpsGateway = configuredBase.startsWith("https://") &&
                !configuredBase.contains(".developer.azure-api.net") &&
                !host.isBlank();

        return new Diagnostics(
                host,
                httpsGateway,
                apiKey != null && !apiKey.isBlank(),
                subscriptionKey != null && !subscriptionKey.isBlank()
        );
    }

    private JsonNode post(String path, Object body) {
        return exchange(
                normalizedBaseUrl() + path,
                HttpMethod.POST,
                body
        );
    }

    private JsonNode exchange(
            String url,
            HttpMethod method,
            Object body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setCacheControl(CacheControl.noCache());
        headers.set(API_KEY_HEADER, apiKey.trim());

        if (subscriptionKey != null && !subscriptionKey.isBlank()) {
            headers.set(SUBSCRIPTION_HEADER, subscriptionKey.trim());
        }

        HttpEntity<?> entity = body == null
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    method,
                    entity,
                    JsonNode.class
            );

            JsonNode payload = response.getBody();
            if (payload == null) {
                throw new WemaProviderException(
                        "WEMA_EMPTY_RESPONSE",
                        "Wema returned an empty response.",
                        response.getStatusCode().value()
                );
            }
            return payload;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new WemaProviderException(
                        "WEMA_AUTH_REJECTED",
                        "Wema rejected the Wallet Services credentials or this channel is not subscribed to the required product.",
                        status,
                        ex
                );
            }
            if (status == 404) {
                throw new WemaProviderException(
                        "WEMA_ENDPOINT_UNAVAILABLE",
                        "The Wema Wallet Services endpoint is not available for the configured sandbox gateway or subscription.",
                        status,
                        ex
                );
            }
            if (status == 429) {
                throw new WemaProviderException(
                        "WEMA_RATE_LIMIT",
                        "Wema temporarily rate-limited the wallet request. Try again shortly.",
                        status,
                        ex
                );
            }
            throw new WemaProviderException(
                    "WEMA_UPSTREAM_ERROR",
                    "Wema Wallet Services returned HTTP " + status + ".",
                    status,
                    ex
            );
        } catch (ResourceAccessException ex) {
            throw new WemaProviderException(
                    "WEMA_NETWORK_ERROR",
                    "The Wave server could not reach Wema Wallet Services. Try again shortly.",
                    null,
                    ex
            );
        }
    }

    private void assertSuccessful(JsonNode response, String fallback) {
        JsonNode statusNode = response.path("status");

        if (statusNode.isBoolean() && !statusNode.asBoolean()) {
            throw providerRejected(response, fallback);
        }

        JsonNode successfulNode = response.path("successful");
        if (successfulNode.isBoolean() && !successfulNode.asBoolean()) {
            throw providerRejected(response, fallback);
        }

        JsonNode hasErrorNode = response.path("hasError");
        if (hasErrorNode.isBoolean() && hasErrorNode.asBoolean()) {
            throw providerRejected(response, fallback);
        }

        if (statusNode.isTextual()) {
            String status = statusNode.asText("").trim().toUpperCase();
            if (status.equals("FAILED") ||
                    status.equals("FAILURE") ||
                    status.equals("ERROR") ||
                    status.equals("INVALID") ||
                    status.equals("REJECTED")) {
                throw providerRejected(response, fallback);
            }
        }
    }

    private WemaProviderException providerRejected(
            JsonNode response,
            String fallback
    ) {
        return new WemaProviderException(
                "WEMA_REQUEST_REJECTED",
                message(response, fallback),
                null
        );
    }

    private String message(JsonNode response, String fallback) {
        String value = firstExpectedText(
                response,
                "message",
                "Message",
                "errorMessage",
                "responseDescription",
                "ResponseMessage"
        );
        return value.isBlank() ? fallback : value;
    }

    private String firstExpectedText(JsonNode response, String... names) {
        for (String name : names) {
            String direct = text(response, name);
            if (!direct.isBlank()) {
                return direct;
            }
        }

        for (String container : new String[]{"data", "result"}) {
            JsonNode node = response.path(container);
            if (node.isObject()) {
                for (String name : names) {
                    String value = text(node, name);
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        return firstRecursiveText(response, names);
    }

    private String text(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode value = node.get(name);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return "";
        }
        return value.asText("").trim();
    }

    private String firstRecursiveText(JsonNode node, String... names) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }

        if (node.isObject()) {
            for (String name : names) {
                String direct = text(node, name);
                if (!direct.isBlank()) {
                    return direct;
                }
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                String value = firstRecursiveText(
                        fields.next().getValue(),
                        names
                );
                if (!value.isBlank()) {
                    return value;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String value = firstRecursiveText(child, names);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }

        return "";
    }

    private void requireCredentials() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new WemaProviderException(
                    "WEMA_API_KEY_MISSING",
                    "Wema Wallet Services x-api-key is not configured on the Wave server.",
                    null
            );
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new WemaProviderException(
                    "WEMA_BASE_URL_INVALID",
                    "Wema Wallet Services base URL must use HTTPS.",
                    null
            );
        }
        if (value.contains(".developer.azure-api.net")) {
            throw new WemaProviderException(
                    "WEMA_BASE_URL_INVALID",
                    "The Wema developer portal hostname cannot be used for API calls; configure the API gateway hostname instead.",
                    null
            );
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    public record StartResult(String trackingId, String message) {
    }

    public record ProviderMessage(String message) {
    }

    public record PartnershipAccountDetails(
            String accountNumber,
            String message
    ) {
    }

    public record WalletAccountDetails(
            String walletNumber,
            BigDecimal availableBalance,
            String walletStatus
    ) {
    }

    public record Diagnostics(
            String gatewayHost,
            boolean gatewayLooksValid,
            boolean apiKeyConfigured,
            boolean subscriptionKeyConfigured
    ) {
    }
}
