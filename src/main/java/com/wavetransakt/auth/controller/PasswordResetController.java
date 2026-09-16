package com.wavetransakt.auth.controller;

import com.wavetransakt.auth.dto.ForgotPasswordRequest;
import com.wavetransakt.auth.dto.ResetPasswordRequest;
import com.wavetransakt.auth.service.PasswordResetService;
import com.wavetransakt.security.ratelimit.RateLimitGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final RateLimitGuard rateLimitGuard;

    @PostMapping("/forgot")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        rateLimitGuard.requireAllowed(
                "RECOVERY_REQUEST_IDENTIFIER",
                rateLimitGuard.canonicalIdentifier(request.getIdentifier()),
                3,
                Duration.ofMinutes(15)
        );
        String controlledCode = passwordResetService.requestPasswordReset(request);
        return ResponseEntity.ok(
                new PasswordResetResponse(
                        "If the account exists, a recovery code has been sent to its registered phone number.",
                        controlledCode
                )
        );
    }

    @PostMapping("/reset")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        rateLimitGuard.requireAllowed(
                "RECOVERY_RESET_IDENTIFIER",
                rateLimitGuard.canonicalIdentifier(request.getIdentifier()),
                8,
                Duration.ofMinutes(15)
        );
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(
                new PasswordResetResponse(
                        "Account PIN reset successful. Sign in with your new PIN.",
                        null
                )
        );
    }

    public record PasswordResetResponse(
            String message,
            String resetCode
    ) {}
}
