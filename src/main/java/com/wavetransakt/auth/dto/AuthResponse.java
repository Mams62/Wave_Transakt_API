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

    /** Full authenticated JWT. Null until every required login factor succeeds. */
    private String token;

    private String verificationCode;

    /** True when a credential login still requires the registered-phone OTP. */
    private Boolean requiresOtp;

    /** Masked registered phone number only; the full number is never returned. */
    private String maskedPhone;

    /** True when the login still requires server-verified liveness + identity match. */
    private Boolean faceVerificationRequired;

    /**
     * Short-lived opaque pre-auth challenge. It is not a JWT and grants no access to
     * authenticated wallet APIs. It is returned only after the phone OTP succeeds.
     */
    private String faceChallengeToken;
}
