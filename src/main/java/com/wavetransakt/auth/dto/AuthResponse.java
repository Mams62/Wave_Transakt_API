package com.wavetransakt.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String message;

    private String token;

    private String verificationCode;

    /** True when a credential login still requires the registered-phone OTP. */
    private Boolean requiresOtp;

    /** Masked registered phone number only; the full number is never returned. */
    private String maskedPhone;

    /** Cross-device face verification is not marked complete until a real provider is wired. */
    private Boolean faceVerificationRequired;
}
