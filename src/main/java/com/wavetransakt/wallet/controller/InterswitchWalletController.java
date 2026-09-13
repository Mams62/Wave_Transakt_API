package com.wavetransakt.wallet.controller;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.InterswitchDiagnosticsResponse;
import com.wavetransakt.wallet.interswitch.InterswitchAuthClient;
import com.wavetransakt.wallet.interswitch.InterswitchWalletClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlled-stage Interswitch endpoints.
 *
 * No money movement endpoint is exposed here. This controller exists only to
 * verify safe server-side configuration while provider onboarding is pending.
 */
@RestController
@RequestMapping("/api/v1/wallet/interswitch")
public class InterswitchWalletController {

    private final InterswitchAuthClient authClient;
    private final InterswitchWalletClient walletClient;
    private final boolean moneyMovementEnabled;

    public InterswitchWalletController(
            InterswitchAuthClient authClient,
            InterswitchWalletClient walletClient,
            @Value("${wave.wallet.local-money-movement-enabled:false}") boolean moneyMovementEnabled
    ) {
        this.authClient = authClient;
        this.walletClient = walletClient;
        this.moneyMovementEnabled = moneyMovementEnabled;
    }

    /**
     * Returns configuration PRESENCE only. No credentials, access tokens,
     * wallet identifiers, phone numbers, NIN/BVN values or raw provider data.
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<InterswitchDiagnosticsResponse> diagnostics(
            Authentication authentication
    ) {
        authenticatedUser(authentication);

        InterswitchAuthClient.Diagnostics auth = authClient.diagnostics();
        InterswitchWalletClient.Diagnostics wallet = walletClient.diagnostics();

        return ResponseEntity.ok(new InterswitchDiagnosticsResponse(
                auth.enabled(),
                auth.configured() && wallet.configured(),
                auth.passportHost(),
                wallet.walletHost(),
                auth.clientIdConfigured(),
                auth.clientSecretConfigured(),
                wallet.domain(),
                wallet.channel(),
                wallet.walletIdType(),
                moneyMovementEnabled
        ));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }
}
