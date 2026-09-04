package com.wavetransakt.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(
            message = "Email or phone is required"
    )
    private String identifier;
}