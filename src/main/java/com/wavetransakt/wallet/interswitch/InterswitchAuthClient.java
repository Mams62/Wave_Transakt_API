package com.wavetransakt.wallet.interswitch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Server-side OAuth client for Interswitch Passport.
 *
 * The client ID and secret are backend-only credentials. They must be supplied
 * through Render/environment secrets and must never be shipped in Android, iOS,
 * POS builds, logs, or API responses.
 */
@Component
public class InterswitchAuthClient {

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    public InterswitchAuthClient(
            RestTemplate restTemplate,
            @Value("${interswitch.enabled:false}") boolean enabled,
            @Value("${interswitch.passport.token-url:https://passport-sandbox.interswitchng.com/passport/oauth/token}") String tokenUrl,
            @Value("${interswitch.client-id:}") String clientId,
            @Value("${interswitch.client-secret:}") String clientSecret
    ) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.tokenUrl = tokenUrl == null ? "" : tokenUrl.trim();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    public boolean isConfigured() {
        return enabled
                && !tokenUrl.isBlank()
                && !clientId.isBlank()
                && !clientSecret.isBlank();
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                enabled,
                isConfigured(),
                safeHost(tokenUrl),
                !clientId.isBlank(),
                !clientSecret.isBlank()
        );
    }

    public String getAccessToken() {
        requireConfigured();

        String rawCredentials = clientId + ":" + clientSecret;
        String basicCredentials = Base64.getEncoder().encodeToString(
                rawCredentials.getBytes(StandardCharsets.UTF_8)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basicCredentials);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUrl + (tokenUrl.contains("?") ? "&" : "?") + "grant_type=client_credentials",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new InterswitchProviderException(
                        "Interswitch authentication returned an empty response"
                );
            }

            Object token = body.get("access_token");
            if (token == null || token.toString().isBlank()) {
                throw new InterswitchProviderException(
                        "Interswitch authentication did not return an access token"
                );
            }

            return token.toString().trim();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new InterswitchProviderException(
                        "Interswitch credentials were rejected by the provider"
                );
            }
            throw new InterswitchProviderException(
                    "Interswitch authentication is temporarily unavailable"
            );
        } catch (RestClientException e) {
            throw new InterswitchProviderException(
                    "Interswitch authentication is temporarily unavailable",
                    e
            );
        }
    }

    private void requireConfigured() {
        if (!enabled) {
            throw new InterswitchProviderException(
                    "Interswitch integration is disabled on the server"
            );
        }
        if (tokenUrl.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            throw new InterswitchProviderException(
                    "Interswitch integration is not configured on the server"
            );
        }
    }

    private String safeHost(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.net.URI.create(value).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    public record Diagnostics(
            boolean enabled,
            boolean configured,
            String passportHost,
            boolean clientIdConfigured,
            boolean clientSecretConfigured
    ) {
    }
}
