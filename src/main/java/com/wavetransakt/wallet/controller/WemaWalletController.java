package com.wavetransakt.wallet.controller;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.WemaDiagnosticsResponse;
import com.wavetransakt.wallet.dto.WemaOtpRequest;
import com.wavetransakt.wallet.dto.WemaWalletActionResponse;
import com.wavetransakt.wallet.service.WemaWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/wema")
@RequiredArgsConstructor
public class WemaWalletController {

    private final WemaWalletService wemaWalletService;

    @PostMapping("/onboarding/start")
    public ResponseEntity<WemaWalletActionResponse> start(
            Authentication authentication
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                wemaWalletService.startOnboarding(user.getId())
        );
    }

    @PostMapping("/onboarding/otp")
    public ResponseEntity<WemaWalletActionResponse> otp(
            Authentication authentication,
            @Valid @RequestBody WemaOtpRequest request
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                wemaWalletService.verifyOtp(user.getId(), request.otp())
        );
    }

    @PostMapping("/onboarding/refresh")
    public ResponseEntity<WemaWalletActionResponse> refresh(
            Authentication authentication
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                wemaWalletService.refreshOnboarding(user.getId())
        );
    }

    /**
     * Controlled-test diagnostics. Returns configuration presence only and never
     * exposes Wema keys, NIN, BVN, OTP or tracking IDs.
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<WemaDiagnosticsResponse> diagnostics(
            Authentication authentication
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                wemaWalletService.diagnostics(user.getId())
        );
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
