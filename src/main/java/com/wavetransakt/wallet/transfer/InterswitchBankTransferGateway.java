package com.wavetransakt.wallet.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.wallet.interswitch.InterswitchAuthClient;
import com.wavetransakt.wallet.interswitch.InterswitchProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interswitch Quickteller Service v5 Send Money transport.
 *
 * Provider-assigned product values are deliberately configuration-only. Wave
 * must never guess an entity code, terminal ID, transfer prefix or funding
 * payment-method/channel configuration.
 */
@Component
@RequiredArgsConstructor
public class InterswitchBankTransferGateway implements BankTransferGateway {

    private final RestTemplate restTemplate;
    private final InterswitchAuthClient authClient;

    @Value("${interswitch.transfer.enabled:false}")
    private boolean enabled;

    @Value("${interswitch.transfer.product-approved:false}")
    private boolean productApproved;

    @Value("${interswitch.transfer.base-url:https://qa.interswitchng.com/quicktellerservice/api/v5}")
    private String baseUrl;

    @Value("${interswitch.transfer.terminal-id:}")
    private String terminalId;

    @Value("${interswitch.transfer.initiating-entity-code:}")
    private String initiatingEntityCode;

    @Value("${interswitch.transfer.transfer-code-prefix:}")
    private String transferCodePrefix;

    @Value("${interswitch.transfer.initiation-payment-method-code:}")
    private String initiationPaymentMethodCode;

    @Value("${interswitch.transfer.initiation-channel:}")
    private String initiationChannel;

    @Value("${interswitch.transfer.termination-payment-method-code:AC}")
    private String terminationPaymentMethodCode;

    @Value("${interswitch.transfer.termination-account-type:00}")
    private String terminationAccountType;

    @Override
    public String code() {
        return "INTERSWITCH";
    }

    @Override
    public Readiness readiness() {
        boolean readConfigured = enabled
                && authClient.isConfigured()
                && httpsBaseUrlConfigured()
                && has(terminalId);

        boolean writeConfigured = readConfigured
                && productApproved
                && has(initiatingEntityCode)
                && transferCodePrefix != null
                && transferCodePrefix.trim().matches("\\d{4}")
                && has(initiationPaymentMethodCode)
                && has(initiationChannel)
                && has(terminationPaymentMethodCode)
                && has(terminationAccountType);

        String status;
        if (!enabled) {
            status = "DISABLED";
        } else if (!authClient.isConfigured() || !httpsBaseUrlConfigured() || !has(terminalId)) {
            status = "AWAITING_READ_CONFIGURATION";
        } else if (!productApproved) {
            status = "AWAITING_PROVIDER_APPROVAL";
        } else if (!writeConfigured) {
            status = "AWAITING_TRANSFER_PRODUCT_CONFIGURATION";
        } else {
            status = "READY";
        }

        return new Readiness(
                code(),
                enabled,
                productApproved,
                readConfigured,
                writeConfigured,
                writeConfigured,
                status
        );
    }

    @Override
    public List<Bank> banks() {
        requireReadConfigured();
        JsonNode payload = exchange(
                "/configuration/fundstransferbanks",
                HttpMethod.GET,
                null,
                Map.of(),
                null
        );

        Map<String, Bank> unique = new LinkedHashMap<>();
        collectBanks(payload, unique);
        List<Bank> banks = new ArrayList<>(unique.values());
        banks.sort(Comparator.comparing(Bank::bankName, String.CASE_INSENSITIVE_ORDER));
        if (banks.isEmpty()) {
            throw new InterswitchProviderException("Interswitch returned no supported transfer banks");
        }
        return banks;
    }

    @Override
    public NameEnquiry resolveAccount(String bankCode, String accountNumber) {
        requireReadConfigured();
        String safeBankCode = requireBankCode(bankCode);
        String safeAccountNumber = requireAccountNumber(accountNumber);

        JsonNode payload = exchange(
                "/transactions/DoAccountNameInquiry",
                HttpMethod.GET,
                null,
                Map.of(
                        "bankCode", safeBankCode,
                        "accountId", safeAccountNumber
                ),
                null
        );

        String responseCode = firstText(payload, "ResponseCode", "responseCode", "code");
        String grouping = firstText(payload, "ResponseCodeGrouping", "responseCodeGrouping", "status");
        String accountName = firstText(payload, "AccountName", "accountName", "account_name");
        String message = firstText(payload, "ResponseMessage", "responseMessage", "message", "Message");
        boolean valid = "90000".equals(responseCode)
                || "SUCCESSFUL".equalsIgnoreCase(grouping);
        valid = valid && !accountName.isBlank();

        if (!valid && message.isBlank()) {
            message = "The beneficiary account could not be verified";
        }

        return new NameEnquiry(
                code(),
                safeBankCode,
                safeAccountNumber,
                accountName,
                responseCode,
                message,
                valid
        );
    }

    @Override
    public TransferResult transfer(TransferCommand command) {
        requireWriteConfigured();
        if (command == null) {
            throw new IllegalArgumentException("Transfer command is required");
        }

        String requestReference = requireReference(command.requestReference());
        String transferCode = requireTransferCode(command.transferCode());
        String destinationBankCode = requireBankCode(command.destinationBankCode());
        String destinationAccount = requireAccountNumber(command.destinationAccountNumber());
        long minorAmount = toMinorUnits(command.amount());
        String amount = Long.toString(minorAmount);

        String mac = sha512(
                amount
                        + "566"
                        + initiationPaymentMethodCode.trim()
                        + amount
                        + "566"
                        + terminationPaymentMethodCode.trim()
                        + "NG"
        );

        Map<String, Object> accountReceivable = new LinkedHashMap<>();
        accountReceivable.put("accountNumber", destinationAccount);
        accountReceivable.put("accountType", terminationAccountType.trim());

        Map<String, Object> termination = new LinkedHashMap<>();
        termination.put("amount", amount);
        termination.put("accountReceivable", accountReceivable);
        termination.put("entityCode", destinationBankCode);
        termination.put("currencyCode", "566");
        termination.put("paymentMethodCode", terminationPaymentMethodCode.trim());
        termination.put("countryCode", "NG");

        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("phone", safeOptional(command.senderPhone(), 25));
        sender.put("email", safeOptional(command.senderEmail(), 100));
        sender.put("lastname", safeRequired(command.senderLastName(), "Sender last name", 50));
        sender.put("othernames", safeRequired(command.senderOtherNames(), "Sender other names", 50));

        Map<String, Object> initiation = new LinkedHashMap<>();
        initiation.put("amount", amount);
        initiation.put("currencyCode", "566");
        initiation.put("paymentMethodCode", initiationPaymentMethodCode.trim());
        initiation.put("channel", initiationChannel.trim());

        Map<String, Object> beneficiary = new LinkedHashMap<>();
        beneficiary.put("lastname", safeRequired(command.beneficiaryLastName(), "Beneficiary last name", 50));
        beneficiary.put("othernames", safeRequired(command.beneficiaryOtherNames(), "Beneficiary other names", 50));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transferCode", transferCode);
        body.put("mac", mac);
        body.put("termination", termination);
        body.put("sender", sender);
        body.put("initiatingEntityCode", initiatingEntityCode.trim());
        body.put("initiation", initiation);
        body.put("beneficiary", beneficiary);

        try {
            JsonNode payload = exchange(
                    "/transactions/TransferFunds",
                    HttpMethod.POST,
                    body,
                    Map.of(),
                    requestReference
            );
            return parseTransferResult(payload, requestReference);
        } catch (RetryableProviderException ex) {
            return new TransferResult(
                    code(), Outcome.PENDING, requestReference, null,
                    ex.providerCode, null,
                    "Provider outcome is uncertain; query the same request reference before retrying"
            );
        }
    }

    @Override
    public TransferResult query(String requestReference) {
        requireReadConfigured();
        String safeReference = requireReference(requestReference);
        String path = UriComponentsBuilder
                .fromPath("/Transactions")
                .queryParam("requestRef", safeReference)
                .build()
                .encode()
                .toUriString();

        JsonNode payload = exchange(path, HttpMethod.GET, null, Map.of(), safeReference);
        return parseTransferResult(payload, safeReference);
    }

    public String deterministicTransferCode(String requestReference) {
        requireWriteConfigured();
        String safeReference = requireReference(requestReference);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(safeReference.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }

        StringBuilder digits = new StringBuilder();
        for (byte value : digest) {
            digits.append(Math.floorMod(value, 10));
            if (digits.length() >= 14) break;
        }
        while (digits.length() < 14) digits.append('0');
        return transferCodePrefix.trim() + digits.substring(0, 14);
    }

    private TransferResult parseTransferResult(JsonNode payload, String requestReference) {
        String responseCode = firstText(
                payload,
                "ResponseCode", "responseCode", "transactionResponseCode", "code"
        );
        String grouping = firstText(
                payload,
                "ResponseCodeGrouping", "responseCodeGrouping", "status", "Status"
        );
        String providerReference = firstText(
                payload,
                "TransactionReference", "transactionReference", "transactionRef", "requestReference"
        );
        String message = firstText(
                payload,
                "ResponseMessage", "responseMessage", "message", "Message", "description"
        );

        String normalizedGrouping = grouping.toUpperCase(Locale.ROOT);
        if ("70120".equals(responseCode)) {
            return new TransferResult(
                    code(), Outcome.RETRYABLE, requestReference, providerReference,
                    responseCode, grouping,
                    message.isBlank() ? "Beneficiary bank is temporarily unavailable" : message
            );
        }
        if ("90000".equals(responseCode)
                || normalizedGrouping.equals("SUCCESSFUL")
                || normalizedGrouping.equals("COMPLETED")
                || normalizedGrouping.equals("SUCCESS")) {
            return new TransferResult(
                    code(), Outcome.SUCCESS, requestReference, providerReference,
                    responseCode, grouping,
                    message.isBlank() ? "Transfer completed" : message
            );
        }
        if (normalizedGrouping.equals("FAILED")
                || normalizedGrouping.equals("REVERSED")
                || normalizedGrouping.equals("CANCELLED")) {
            return new TransferResult(
                    code(), Outcome.FAILED, requestReference, providerReference,
                    responseCode, grouping,
                    message.isBlank() ? "Transfer failed" : message
            );
        }
        return new TransferResult(
                code(), Outcome.PENDING, requestReference, providerReference,
                responseCode, grouping,
                message.isBlank() ? "Transfer status is pending confirmation" : message
        );
    }

    private JsonNode exchange(
            String path,
            HttpMethod method,
            Object body,
            Map<String, String> additionalHeaders,
            String requestReference
    ) {
        String token = authClient.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("TerminalId", terminalId.trim());
        additionalHeaders.forEach(headers::set);

        HttpEntity<?> entity = body == null
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    normalizedBaseUrl() + path,
                    method,
                    entity,
                    JsonNode.class
            );
            JsonNode payload = response.getBody();
            if (payload == null) {
                throw new RetryableProviderException("EMPTY_RESPONSE");
            }
            return payload;
        } catch (ResourceAccessException ex) {
            throw new RetryableProviderException("TIMEOUT");
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            if (status >= 500) {
                throw new RetryableProviderException("HTTP_" + status);
            }
            throw new InterswitchProviderException(
                    "Interswitch rejected the bank transfer request"
            );
        } catch (RestClientException ex) {
            throw new RetryableProviderException("NETWORK_ERROR");
        }
    }

    private void requireReadConfigured() {
        Readiness readiness = readiness();
        if (!readiness.enabled()) {
            throw new InterswitchProviderException("Interswitch bank transfers are disabled on the server");
        }
        if (!readiness.readConfigured()) {
            throw new InterswitchProviderException("Interswitch bank-transfer read configuration is incomplete");
        }
    }

    private void requireWriteConfigured() {
        Readiness readiness = readiness();
        if (!readiness.productApproved()) {
            throw new InterswitchProviderException("Interswitch transfer product approval is required");
        }
        if (!readiness.writeConfigured()) {
            throw new InterswitchProviderException("Interswitch transfer product configuration is incomplete");
        }
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (!value.startsWith("https://")) {
            throw new InterswitchProviderException("Interswitch transfer base URL must use HTTPS");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean httpsBaseUrlConfigured() {
        return baseUrl != null && baseUrl.trim().startsWith("https://");
    }

    private String requireBankCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{2,20}")) {
            throw new IllegalArgumentException("Invalid destination bank code");
        }
        return normalized;
    }

    private String requireAccountNumber(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("\\d{10}")) {
            throw new IllegalArgumentException("Destination account number must be exactly 10 digits");
        }
        return normalized;
    }

    private String requireReference(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{8,80}")) {
            throw new IllegalArgumentException("Invalid transfer request reference");
        }
        return normalized;
    }

    private String requireTransferCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("\\d{8,18}")) {
            throw new IllegalArgumentException("Invalid Interswitch transfer code");
        }
        if (!normalized.startsWith(transferCodePrefix.trim())) {
            throw new IllegalArgumentException("Transfer code does not use the assigned Interswitch prefix");
        }
        return normalized;
    }

    private long toMinorUnits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Transfer amount must have at most two decimal places");
        }
    }

    private String safeRequired(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private String safeOptional(String value, int max) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String sha512(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-512")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is unavailable", e);
        }
    }

    private void collectBanks(JsonNode node, Map<String, Bank> result) {
        if (node == null || node.isNull() || node.isMissingNode()) return;
        if (node.isObject()) {
            String bankCode = firstDirectText(
                    node, "bankCode", "BankCode", "code", "Code", "institutionCode"
            );
            String bankName = firstDirectText(
                    node, "bankName", "BankName", "name", "Name", "institutionName"
            );
            if (!bankCode.isBlank() && !bankName.isBlank()) {
                result.putIfAbsent(bankCode, new Bank(bankCode, bankName));
            }
            node.elements().forEachRemaining(child -> collectBanks(child, result));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectBanks(child, result));
        }
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        String direct = firstDirectText(node, fields);
        if (!direct.isBlank()) return direct;
        if (node.isObject()) {
            var iterator = node.elements();
            while (iterator.hasNext()) {
                String nested = firstText(iterator.next(), fields);
                if (!nested.isBlank()) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = firstText(child, fields);
                if (!nested.isBlank()) return nested;
            }
        }
        return "";
    }

    private String firstDirectText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) return "";
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private boolean has(String value) {
        return value != null && !value.isBlank();
    }

    private static final class RetryableProviderException extends RuntimeException {
        private final String providerCode;

        private RetryableProviderException(String providerCode) {
            super(providerCode);
            this.providerCode = providerCode;
        }
    }
}
