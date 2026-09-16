package com.wavetransakt.verification.controller;

import com.wavetransakt.security.ratelimit.RateLimitGuard;
import com.wavetransakt.verification.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;
    private final RateLimitGuard rateLimitGuard;

    @Value("${wave.demo.return-verification-code:false}")
    private boolean returnVerificationCode;

    @PostMapping("/email")
    public ResponseEntity<?> verifyEmail(
            @RequestBody EmailVerificationRequest request
    ) {
        rateLimitGuard.requireAllowed(
                "EMAIL_VERIFY_IDENTIFIER",
                rateLimitGuard.canonicalIdentifier(request.getEmail()),
                10,
                Duration.ofMinutes(10)
        );

        verificationService.verifyEmail(
                request.getEmail(),
                request.getCode()
        );

        return ResponseEntity.ok(
                new VerificationResponse(
                        "Email verified successfully",
                        null
                )
        );
    }

    /**
     * Existing unverified accounts may request a fresh email code. Unknown and
     * already-verified accounts intentionally receive the same public response so
     * this endpoint cannot be used to enumerate Wave accounts. In controlled
     * staging only, a generated code may still be returned when explicitly enabled.
     */
    @PostMapping("/email/resend")
    public ResponseEntity<?> resendEmail(
            @RequestBody ResendEmailVerificationRequest request
    ) {
        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase(Locale.ROOT);

        rateLimitGuard.requireAllowed(
                "EMAIL_RESEND_IDENTIFIER",
                rateLimitGuard.canonicalIdentifier(email),
                4,
                Duration.ofMinutes(15)
        );

        String code = verificationService.resendEmailVerification(email);

        return ResponseEntity.ok(
                new VerificationResponse(
                        "If the account exists and still requires email verification, a new verification code has been created.",
                        returnVerificationCode && code != null ? code : null
                )
        );
    }

    public static class EmailVerificationRequest {
        private String email;
        private String code;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class ResendEmailVerificationRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public record VerificationResponse(
            String message,
            String verificationCode
    ) {
    }
}
