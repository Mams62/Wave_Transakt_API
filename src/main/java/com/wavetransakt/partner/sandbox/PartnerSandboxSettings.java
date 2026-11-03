package com.wavetransakt.partner.sandbox;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A simulated, bank-neutral test harness, NOT an implementation of Wema's
 * unreceived sandbox contract. No outbound payments or credentials are exposed.
 */
@Component
public class PartnerSandboxSettings {
    private final Environment env;

    public PartnerSandboxSettings(Environment env) { this.env = env; }

    public boolean simulationEnabled() {
        return Boolean.parseBoolean(env.getProperty("wave.bank.wema.sandbox-simulation-enabled", "false"));
    }

    public boolean webhookVerificationReady() {
        String secret = env.getProperty("wave.bank.wema.sandbox-webhook-secret", "");
        return simulationEnabled() && secret.length() >= 32;
    }

    public String simulationSecret() {
        if (!webhookVerificationReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sandbox callback verification unavailable");
        }
        return env.getProperty("wave.bank.wema.sandbox-webhook-secret");
    }

    public void requireSimulation() {
        if (!simulationEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bank sandbox simulation is not enabled; no money movement is available");
        }
    }

    public boolean credentialsPresent() {
        // Informational ONLY: does not establish correct Wema entitlement/auth.
        String wallet = env.getProperty("wema.wallet.subscription-key", "");
        String transfer = env.getProperty("wema.transfer.subscription-key", "");
        return !wallet.isBlank() || !transfer.isBlank();
    }

    public boolean bankApiContractVerified() {
        // Must remain false until official Wema onboarding and review of
        // actual auth, webhook, balances and finality contracts.
        return false;
    }

    public boolean realMoneyMovementEnabled() { return false; }
}
