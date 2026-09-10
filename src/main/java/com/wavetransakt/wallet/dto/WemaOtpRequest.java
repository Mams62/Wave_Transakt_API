package com.wavetransakt.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WemaOtpRequest(
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "\\d{4,8}", message = "OTP must contain 4 to 8 digits")
        String otp
) {
}
