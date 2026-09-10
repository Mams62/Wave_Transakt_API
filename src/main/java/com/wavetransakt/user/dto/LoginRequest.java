package com.wavetransakt.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email or phone is required")
    private String identifier;

    @NotBlank(message = "Account PIN is required")
    @Pattern(
            regexp = "^\\d{6}$",
            message = "Account PIN must be exactly 6 digits"
    )
    private String accountPin;
}
