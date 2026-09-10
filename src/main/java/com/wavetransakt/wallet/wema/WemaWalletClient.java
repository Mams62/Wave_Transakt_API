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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WemaWalletClient {

    private static final String SUBSCRIPTION_HEADER = "Ocp-Apim-Subscription-Key";
    private static final String API_KEY_HEADER = "x-api-key";

    private final RestTemplate restTemplate;

    @Value("${wema.base-url:https://playground.azure-api.net}")
    private String baseUrl;

    @Value("${wema.wallet-subscription-key:}")
    private String subscriptionKey;

    @Value("${wema.api-key:}")
    private String apiKey;

    public StartResult startNinWallet(User user) {
        requireCredentials();

        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        if (user.getNin() == null || !user.getNin().matches("\\d{11}")) {
            throw new IllegalArgumentException("A valid 11-digit NIN is required for Wema wallet creation");
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone number is required for Wema wallet creation");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required for Wema wallet creation");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", user.getPhone().trim());
        body.put("email", user.getEmail().trim());
        body.put("nin", user.getNin().trim());

        JsonNode response = post(
                "/wallet-creation/api/CustomerAccount/GenerateWalletAccountForPartnerships/Request",
                body
        );

        assertSuccessful(response, "Wema wallet creation request was rejected");

        String trackingId = firstRecursiveText(
                response,
                "trackingId",
                "trackingID",
                "tracking_id"
        );
        if (trackingId.isBlank()) {
            throw new IllegalStateException(
                    "Wema accepted the wallet request but did not return a tracking ID. " +
                            "Check the Wema channel callback/profile configuration."
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
                message(response, "OTP accepted. Wema wallet creation is pending.")
        );
    }

    public PartnershipAccountDetails getPartnershipAccountDetails(String phoneNumber) {
        requireCredentials();

        String url = UriComponentsBuilder
                .fromHttpUrl(normalizedBaseUrl() +
                        "/wallet-creation/api/CustomerAccount/GetPartnershipAccountDetails")
                .queryParam("phoneNumber", phoneNumber)
                .build(true)
                .toUriString();

        JsonNode response = exchange(url, HttpMethod.GET, null);
        assertSuccessful(response, "Unable to retrieve Wema wallet account details");

        String accountNumber = firstRecursiveText(
                response,
                "accountNumber",
                "walletNumber",
                "nuban",
                "NUBAN"
        );

        return new PartnershipAccountDetails(
                accountNumber,
                message(response, accountNumber.isBlank()
                        ? "Wema wallet is still pending"
                        : "Wema wallet account created")
        );
    }

    public WalletAccountDetails getWalletDetails(String accountNumber) {
        requireCredentials();

        if (accountNumber == null || !accountNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("A valid 10-digit Wema account number is required");
        }

        String url = normalizedBaseUrl() +
                "/ws-acct-mgt/api/AccountMaintenance/CustomerAccount/GetAccountV2/accountNumber/" +
                accountNumber;

        JsonNode response = exchange(url, HttpMethod.GET, null);
        assertSuccessful(response, "Unable to retrieve Wema wallet balance");

        String walletNumber = firstRecursiveText(
                response,
                "walletNumber",
                "accountNumber"
        );
        String balanceRaw = firstRecursiveText(
                response,
                "availableBalance",
                "balance"
        );
        String walletStatus = firstRecursiveText(
                response,
                "walletStatus",
                "status"
        );

        BigDecimal availableBalance;
        try {
            availableBalance = new BigDecimal(balanceRaw.replace(",", "").trim());
        } catch (Exception e) {
            throw new IllegalStateException("Wema returned an invalid wallet balance");
        }

        return new WalletAccountDetails(
                walletNumber.isBlank() ? accountNumber : walletNumber,
                availableBalance,
                walletStatus
        );
    }

    private JsonNode post(String path, Object body) {
        String url = normalizedBaseUrl() + path;
        return exchange(url, HttpMethod.POST, body);
    }

    private JsonNode exchange(String url, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.noCache());
        headers.set(SUBSCRIPTION_HEADER, subscriptionKey.trim());
        headers.set(API_KEY_HEADER, apiKey.trim());

        HttpEntity<?> entity = body == null
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                method,
                entity,
                JsonNode.class
        );

        JsonNode payload = response.getBody();
        if (payload == null) {
            throw new IllegalStateException("Wema returned an empty response");
        }
        return payload;
    }

    private void assertSuccessful(JsonNode response, String fallback) {
        JsonNode statusNode = response.path("status");
        if (statusNode.isBoolean() && !statusNode.asBoolean()) {
            throw new IllegalArgumentException(message(response, fallback));
        }

        JsonNode successfulNode = response.path("successful");
        if (successfulNode.isBoolean() && !successfulNode.asBoolean()) {
            throw new IllegalArgumentException(message(response, fallback));
        }

        JsonNode hasErrorNode = response.path("hasError");
        if (hasErrorNode.isBoolean() && hasErrorNode.asBoolean()) {
            throw new IllegalArgumentException(message(response, fallback));
        }

        String statusText = statusNode.isTextual()
                ? statusNode.asText("").trim()
                : "";
        if (!statusText.isBlank() &&
                statusText.equalsIgnoreCase("FAILED")) {
            throw new IllegalArgumentException(message(response, fallback));
        }
    }

    private String message(JsonNode response, String fallback) {
        String value = firstRecursiveText(
                response,
                "message",
                "Message",
                "errorMessage",
                "responseDescription"
        );
        return value.isBlank() ? fallback : value;
    }

    private String firstRecursiveText(JsonNode node, String... names) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }

        if (node.isObject()) {
            for (String name : names) {
                JsonNode direct = node.get(name);
                if (direct != null && !direct.isNull() && direct.isValueNode()) {
                    String value = direct.asText("").trim();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                String value = firstRecursiveText(fields.next().getValue(), names);
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
        if (subscriptionKey == null || subscriptionKey.isBlank()) {
            throw new IllegalStateException(
                    "Wema Wallet Services subscription key is not configured on the server"
            );
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Wema x-api-key is not configured on the server"
            );
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new IllegalStateException("Wema base URL must use HTTPS");
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    public record StartResult(String trackingId, String message) {
    }

    public record ProviderMessage(String message) {
    }

    public record PartnershipAccountDetails(String accountNumber, String message) {
    }

    public record WalletAccountDetails(
            String walletNumber,
            BigDecimal availableBalance,
            String walletStatus
    ) {
    }
}
