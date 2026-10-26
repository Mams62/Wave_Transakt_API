package com.wavetransakt.identity.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Selection is explicit. No automatic fallback between identity vendors:
 * a failure at one provider never becomes a weaker account approval.
 */
@Component
@Primary
@RequiredArgsConstructor
public class GovernmentIdentityProviderRouter implements GovernmentIdentityLookup {

    private final PremblyGovernmentIdentityClient prembly;
    private final DojahGovernmentIdentityClient dojah;

    @Value("${wave.identity.government.provider:DISABLED}")
    private String configuredProvider;

    public String providerName() {
        String name = configuredProvider == null ? "" : configuredProvider.trim().toUpperCase();
        return switch (name) {
            case "PREMBLY", "DOJAH" -> name;
            default -> "DISABLED";
        };
    }

    private GovernmentIdentityLookup selected() {
        return switch (providerName()) {
            case "PREMBLY" -> prembly;
            case "DOJAH" -> dojah;
            default -> throw new IllegalStateException(
                    "Government identity provider has not been approved and configured");
        };
    }

    @Override
    public boolean isConfigured() {
        return switch (providerName()) {
            case "PREMBLY" -> prembly.isConfigured();
            case "DOJAH" -> dojah.isConfigured();
            default -> false;
        };
    }

    @Override
    public IdentityRecord lookupBvn(String bvn) {
        requireReady();
        return selected().lookupBvn(bvn);
    }

    @Override
    public IdentityRecord lookupNin(String nin) {
        requireReady();
        return selected().lookupNin(nin);
    }

    private void requireReady() {
        if (!isConfigured()) {
            throw new IllegalStateException("Identity service is not available for registration");
        }
    }
}
