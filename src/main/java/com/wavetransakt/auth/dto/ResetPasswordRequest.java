package com.wavetransakt.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(
            message = "Email or phone is required"
    )
    private String identifier;

    @NotBlank(
            message = "Reset code is required"
    )
    @Size(
            min = 6,
            max = 6,
            message = "Reset code must be 6 digits"
    )
    private String code;

    @NotBlank(
            message = "New password is required"
    )
    @Size(
            min = 8,
            message = "Password must be at least 8 characters"
    )
    private String newPassword;
}