package com.wavetransakt.wallet.wema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WemaTransferClient {

    private static final String SUBSCRIPTION_HEADER = "Ocp-Apim-Subscription-Key";
    private static final String ACCESS_HEADER = "access";
    private static final String API_KEY_HEADER = "x-api-key";

    private final RestTemplate restTemplate;

    @Value("${wema.transfer-base-url:https://wema-alatdev-apimgt.azure-api.net}")
    private String baseUrl;

    @Value("${wema.transfer-subscription-key:}")
    private String subscriptionKey;

    @Value("${wema.access-key:}")
    private String accessKey;

    @Value("${wema.api-key:}")
    private String apiKey;

    @Value("${wema.channel-id:}")
    private String channelId;

    public JsonNode clientWalletNameEnquiry(String accountNumber) {
        requireReadCredentials();
        requireAccountNumber(accountNumber);
        return exchange(
                normalizedBaseUrl() + "/wallet-transfer/api/Shared/AccountNameEnquiry/Wallet/" + accountNumber.trim(),
                HttpMethod.GET,
                null,
                false
        );
    }

    public JsonNode getAllBanks() {
        requireReadCredentials();
        return exchange(
                normalizedBaseUrl() + "/wallet-transfer/api/Shared/GetAllBanks",
                HttpMethod.GET,
                null,
                false
        );
    }

    public JsonNode accountNameEnquiry(String bankCode, String accountNumber) {
        requireReadCredentials();
        requireBankCode(bankCode);
        requireAccountNumber(accountNumber);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(
                normalizedBaseUrl() + "/wallet-transfer/api/Shared/AccountNameEnquiry/" +
                        bankCode.trim() + "/" + accountNumber.trim()
        );
        if (channelId != null && !channelId.isBlank()) {
            builder.queryParam("channelId", channelId.trim());
        }

        return exchange(builder.build(true).toUriString(), HttpMethod.GET, null, false);
    }

    public JsonNode getNipCharges() {
        requireReadCredentials();
        return exchange(
                normalizedBaseUrl() + "/wallet-transfer/api/Shared/GetNIPCharges",
                HttpMethod.GET,
                null,
                false
        );
    }

    public JsonNode processClientTransfer(
            String securityInfo,
            java.math.BigDecimal amount,
            String destinationBankCode,
            String destinationBankName,
            String destinationAccountNumber,
            String destinationAccountName,
            String sourceAccountNumber,
            String narration,
            String transactionReference
    ) {
        requireWriteCredentials();
        requireAccountNumber(sourceAccountNumber);
        requireAccountNumber(destinationAccountNumber);
        requireBankCode(destinationBankCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("securityInfo", securityInfo);
        body.put("amount", amount);
        body.put("destinationBankCode", destinationBankCode);
        body.put("destinationBankName", destinationBankName);
        body.put("destinationAccountNumber", destinationAccountNumber);
        body.put("destinationAccountName", destinationAccountName);
        body.put("sourceAccountNumber", sourceAccountNumber);
        body.put("narration", narration);
        body.put("transactionReference", transactionReference);
        body.put("useCustomNarration", true);

        return exchange(
                normalizedBaseUrl() + "/wallet-transfer/api/Shared/ProcessClientTransfer",
                HttpMethod.POST,
                body,
                true
        );
    }

    private JsonNode exchange(
            String url,
            HttpMethod method,
            Object body,
            boolean includeAccess
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SUBSCRIPTION_HEADER, subscriptionKey.trim());

        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(API_KEY_HEADER, apiKey.trim());
        }
        if (includeAccess) {
            headers.set(ACCESS_HEADER, accessKey.trim());
        }

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
            throw new IllegalStateException("Wema Wallet Transfer API returned an empty response");
        }
        return payload;
    }

    private void requireReadCredentials() {
        if (subscriptionKey == null || subscriptionKey.isBlank()) {
            throw new IllegalStateException(
                    "Wema Wallet Transfer subscription key is not configured on the server"
            );
        }
    }

    private void requireWriteCredentials() {
        requireReadCredentials();
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException(
                    "Wema Wallet Transfer access key is not configured on the server"
            );
        }
    }

    private void requireAccountNumber(String value) {
        if (value == null || !value.trim().matches("\\d{10}")) {
            throw new IllegalArgumentException("A valid 10-digit account number is required");
        }
    }

    private void requireBankCode(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 20) {
            throw new IllegalArgumentException("A valid destination bank code is required");
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new IllegalStateException("Wema Wallet Transfer base URL must use HTTPS");
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
