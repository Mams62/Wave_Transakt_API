package com.wavetransakt.serviceprovider.vtpass;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.Account;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.VerifyEmailResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VtpassSmileClient {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );

    private final RestTemplate restTemplate;

    @Value("${vtpass.base-url}")
    private String baseUrl;

    @Value("${vtpass.api-key:}")
    private String apiKey;

    @Value("${vtpass.secret-key:}")
    private String secretKey;

    public VerifyEmailResponse verifyEmail(String rawEmail) {
        validateConfigured();
        String email = normalizeEmail(rawEmail);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("billersCode", email);
        form.add("serviceID", "smile-direct");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", apiKey.trim());
        headers.set("secret-key", secretKey.trim());

        JsonNode body;
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    normalizedBaseUrl() + "merchant-verify/smile/email",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    JsonNode.class
            );
            body = response.getBody();
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Smile verification timed out; try again before paying");
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Provider rejected Smile email verification");
        }

        if (body == null) {
            throw new IllegalStateException("Provider returned no Smile verification response");
        }

        String code = text(body, "code");
        JsonNode content = body.path("content");
        String customerName = firstText(content, "Customer_Name", "customer_name", "name");
        List<Account> accounts = parseAccounts(content.path("AccountList").path("Account"));
        boolean valid = "000".equals(code) && !accounts.isEmpty();

        String message = valid
                ? "Smile email verified"
                : firstText(body, "response_description", "message");
        if (!valid && message.isBlank()) {
            message = "The Smile email could not be verified";
        }

        return new VerifyEmailResponse(
                "VTPASS",
                "smile-direct",
                email,
                customerName,
                List.copyOf(accounts),
                valid,
                message
        );
    }

    public boolean containsAccount(VerifyEmailResponse verification, String rawAccountId) {
        if (verification == null || !verification.valid()) {
            return false;
        }
        String accountId = normalizeAccountId(rawAccountId);
        return verification.accounts().stream()
                .anyMatch(account -> accountId.equalsIgnoreCase(account.accountId()));
    }

    public String normalizeAccountId(String rawAccountId) {
        String accountId = rawAccountId == null ? "" : rawAccountId.trim();
        if (!accountId.matches("[A-Za-z0-9_-]{5,40}")) {
            throw new IllegalArgumentException("Invalid Smile account ID");
        }
        return accountId;
    }

    private List<Account> parseAccounts(JsonNode node) {
        List<Account> accounts = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return accounts;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                addAccount(accounts, item);
            }
        } else if (node.isObject()) {
            addAccount(accounts, node);
        }
        return accounts;
    }

    private void addAccount(List<Account> accounts, JsonNode item) {
        String accountId = firstText(item, "AccountId", "accountId", "account_id");
        if (!accountId.matches("[A-Za-z0-9_-]{5,40}")) {
            return;
        }
        String friendlyName = firstText(item, "FriendlyName", "friendlyName", "friendly_name");
        accounts.add(new Account(accountId, friendlyName));
    }

    private void validateConfigured() {
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("VTpass verification credentials are not configured on the server");
        }
    }

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid Smile email");
        }
        return email;
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
}
