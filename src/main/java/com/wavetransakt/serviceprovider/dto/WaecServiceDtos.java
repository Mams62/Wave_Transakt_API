package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public final class WaecServiceDtos {

    private WaecServiceDtos() {
    }

    public record ResultCheckerPurchaseRequest(
            @NotBlank(message = "WAEC variation code is required")
            @Pattern(
                    regexp = "[A-Za-z0-9_-]{1,120}",
                    message = "Invalid WAEC variation code"
            )
            String variationCode,

            BigDecimal amount,

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

    public record RegistrationPurchaseRequest(
            @NotBlank(message = "WAEC registration variation code is required")
            @Pattern(
                    regexp = "[A-Za-z0-9_-]{1,120}",
                    message = "Invalid WAEC registration variation code"
            )
            String variationCode,

            BigDecimal amount,

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
