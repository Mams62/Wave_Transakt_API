package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public final class JambServiceDtos {

    private JambServiceDtos() {
    }

    public record PurchaseRequest(
            @NotBlank(message = "JAMB variation code is required")
            @Pattern(
                    regexp = "[A-Za-z0-9_-]{1,120}",
                    message = "Invalid JAMB variation code"
            )
            String variationCode,

            BigDecimal amount,

            @NotBlank(message = "JAMB Profile ID is required")
            @Pattern(
                    regexp = "\\d{10}",
                    message = "JAMB Profile ID must contain exactly 10 digits"
            )
            String profileId,

            @NotBlank(message = "Customer phone is required")
            @Pattern(
                    regexp = "\\d{10,15}",
                    message = "Customer phone must contain 10 to 15 digits"
            )
            String customerPhone,

            @NotBlank(message = "Transaction PIN is required")
            @Pattern(
                    regexp = "\\d{6}",
                    message = "Transaction PIN must be exactly 6 digits"
            )
            String transactionPin
    ) {
    }
}
