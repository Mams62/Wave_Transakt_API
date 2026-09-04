package com.wavetransakt.auth.controller;

import com.wavetransakt.auth.dto.ForgotPasswordRequest;
import com.wavetransakt.auth.dto.ResetPasswordRequest;
import com.wavetransakt.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Request a password reset code.
     *
     * DEMO MODE:
     * Returns the code directly.
     *
     * Production should send the code
     * through email/SMS instead.
     */
    @PostMapping("/forgot")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody
            ForgotPasswordRequest request
    ) {

        String code =
                passwordResetService
                        .requestPasswordReset(request);

        return ResponseEntity.ok(
                new PasswordResetResponse(
                        "Password reset code generated",
                        code
                )
        );
    }

    /**
     * Reset password using the reset code.
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody
            ResetPasswordRequest request
    ) {

        passwordResetService
                .resetPassword(request);

        return ResponseEntity.ok(
                new PasswordResetResponse(
                        "Password reset successful",
                        null
                )
        );
    }

    /**
     * Small response object.
     */
    public record PasswordResetResponse(
            String message,
            String resetCode
    ) {
    }
}