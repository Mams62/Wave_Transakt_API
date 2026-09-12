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

    @PostMapping("/forgot")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
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
