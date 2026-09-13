package com.wavetransakt.wallet.interswitch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only Interswitch Generic Wallet client for the first integration phase.
 *
 * Money movement is deliberately not implemented here. The controlled staging
 * build keeps WAVE_LOCAL_MONEY_MOVEMENT_ENABLED=false until the provider
 * contract, settlement flow, and production controls are completed.
 */
@Component
public class InterswitchWalletClient {

    private final RestTemplate restTemplate;
    private final InterswitchAuthClient authClient;
    private final String baseUrl;
    private final String domain;
    private final String channel;
    private final String walletIdType;

    public InterswitchWalletClient(
            RestTemplate restTemplate,
            InterswitchAuthClient authClient,
            @Value("${interswitch.wallet.base-url:https://api-gateway.interswitchng.com/generic-wallet}") String baseUrl,
            @Value("${interswitch.wallet.domain:ISW}") String domain,
            @Value("${interswitch.wallet.channel:SERVICE}") String channel,
            @Value("${interswitch.wallet.id-type:PHONE}") String walletIdType
    ) {
        this.restTemplate = restTemplate;
        this.authClient = authClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.domain = normalize(domain, "ISW");
        this.channel = normalize(channel, "SERVICE");
        this.walletIdType = normalize(walletIdType, "PHONE");
    }

    public boolean isConfigured() {
        return authClient.isConfigured() && !baseUrl.isBlank();
    }

    /**
     * Exposes only the configured identifier TYPE, never the identifier value.
     */
    public String walletIdType() {
        return walletIdType;
    }

    public BalanceResult getBalance(String walletId) {
        if (walletId == null || walletId.isBlank()) {
            throw new IllegalArgumentException("Interswitch wallet identifier is required");
        }
        if (!isConfigured()) {
            throw new InterswitchProviderException(
                    "Interswitch wallet integration is not configured on the server"
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authClient.getAccessToken());

        Map<String, Object> request = Map.of(
                "walletIdType", walletIdType,
                "channel", channel,
                "walletid", walletId.trim(),
                "domain", domain
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/api/v1/cards/balance",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new InterswitchProviderException(
                        "Interswitch wallet balance returned an empty response"
                );
            }

            String responseCode = stringValue(body.get("responseCode"));
            if (responseCode != null && !responseCode.equals("00")) {
                throw new InterswitchProviderException(
                        "Interswitch wallet balance request was not approved"
                );
            }

            BigDecimal balance = findBalance(body);
            if (balance == null) {
                throw new InterswitchProviderException(
                        "Interswitch wallet balance response did not contain a usable balance"
                );
            }

            return new BalanceResult(
                    balance,
                    stringValue(body.get("responseMessage"))
            );
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new InterswitchProviderException(
                        "Interswitch wallet credentials are not authorized for this service"
                );
            }
            throw new InterswitchProviderException(
                    "Interswitch wallet balance is temporarily unavailable"
            );
        } catch (RestClientException e) {
            throw new InterswitchProviderException(
                    "Interswitch wallet balance is temporarily unavailable",
                    e
            );
        }
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                isConfigured(),
                safeHost(baseUrl),
                domain,
                channel,
                walletIdType
        );
    }

    private BigDecimal findBalance(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null
                        ? ""
                        : entry.getKey().toString().toLowerCase(Locale.ROOT);
                if (key.equals("availablebalance") ||
                        key.equals("walletbalance") ||
                        key.equals("balance")) {
                    BigDecimal parsed = decimalValue(entry.getValue());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
            for (Object nested : map.values()) {
                BigDecimal parsed = findBalance(nested);
                if (parsed != null) {
                    return parsed;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object nested : list) {
                BigDecimal parsed = findBalance(nested);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String safeHost(String value) {
        try {
            return java.net.URI.create(value).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    public record BalanceResult(BigDecimal balance, String message) {
    }

    public record Diagnostics(
            boolean configured,
            String walletHost,
            String domain,
            String channel,
            String walletIdType
    ) {
    }
}
