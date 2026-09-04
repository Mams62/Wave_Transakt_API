package com.wavetransakt.verification.controller;

import com.wavetransakt.verification.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

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
                        "Email verified successfully"
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

    public record VerificationResponse(String message) {
    }
}