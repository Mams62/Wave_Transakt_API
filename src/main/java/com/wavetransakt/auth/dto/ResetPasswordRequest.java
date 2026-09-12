package com.wavetransakt.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Email or phone is required")
    private String identifier;

    @NotBlank(message = "Recovery code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Recovery code must be exactly 6 digits")
    private String code;

    /**
     * Kept as newPassword in the transport contract for backward compatibility,
     * but Wave Transakt uses a six-digit account PIN rather than a password.
     */
    @NotBlank(message = "New account PIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "Account PIN must be exactly 6 digits")
    private String newPassword;
}
