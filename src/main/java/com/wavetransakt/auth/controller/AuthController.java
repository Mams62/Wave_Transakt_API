package com.wavetransakt.auth.controller;

import com.wavetransakt.auth.dto.AuthResponse;
import com.wavetransakt.auth.dto.LoginOtpRequest;
import com.wavetransakt.auth.service.AuthService;
import com.wavetransakt.user.dto.LoginRequest;
import com.wavetransakt.user.dto.RegisterRequest;
import com.wavetransakt.user.dto.UserProfileResponse;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WalletRepository walletRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authService.register(request));
    }

    /** Starts a credential login challenge. No JWT is issued yet. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Completes the registered-phone OTP challenge and only then issues a JWT. */
    @PostMapping("/login/otp")
    public ResponseEntity<AuthResponse> verifyLoginOtp(
            @Valid @RequestBody LoginOtpRequest request
    ) {
        return ResponseEntity.ok(authService.verifyLoginOtp(request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(401)
                    .body("Authentication required");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof User user)) {
            return ResponseEntity
                    .status(401)
                    .body("Invalid authentication");
        }

        String walletNumber = walletRepository
                .findByUserId(user.getId())
                .map(Wallet::getWalletNumber)
                .orElse("");

        return ResponseEntity.ok(
                UserProfileResponse.from(user, walletNumber)
        );
    }
}
