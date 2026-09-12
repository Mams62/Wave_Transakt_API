package com.wavetransakt.verification.controller;

import com.wavetransakt.verification.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @Value("${wave.demo.return-verification-code:false}")
    private boolean returnVerificationCode;

    @PostMapping("/email")
    public ResponseEntity<?> verifyEmail(
            @RequestBody EmailVerificationRequest request
    ) {
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
     * Allows an existing unverified account to request a fresh email code
     * instead of registering again. In controlled staging only, the generated
     * code may be returned while external email delivery is not configured.
     */
    @PostMapping("/email/resend")
    public ResponseEntity<?> resendEmail(
            @RequestBody ResendEmailVerificationRequest request
    ) {
        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase(Locale.ROOT);

        String code = verificationService.resendEmailVerification(email);

        return ResponseEntity.ok(
                new VerificationResponse(
                        "A new email verification code has been created.",
                        returnVerificationCode ? code : null
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
