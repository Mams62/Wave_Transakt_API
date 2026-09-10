package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ServicePurchaseRequest(
        @NotBlank(message = "Service ID is required")
        String serviceId,

        String variationCode,

        BigDecimal amount,

        @NotBlank(message = "Recipient is required")
        String recipient,

        @NotBlank(message = "Transaction PIN is required")
        @Pattern(regexp = "\\d{6}", message = "Transaction PIN must be exactly 6 digits")
        String transactionPin
) {
}
