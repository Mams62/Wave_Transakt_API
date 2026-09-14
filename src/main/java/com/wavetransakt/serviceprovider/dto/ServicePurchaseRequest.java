package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ServicePurchaseRequest(
        @NotBlank(message = "Service kind is required")
        @Pattern(
                regexp = "(?i)AIRTIME|DATA|ELECTRICITY|TV|INTERNET",
                message = "Service kind must be AIRTIME, DATA, ELECTRICITY, TV or INTERNET"
        )
        String serviceKind,

        @NotBlank(message = "Service ID is required")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{1,80}",
                message = "Invalid service ID"
        )
        String serviceId,

        String variationCode,

        BigDecimal amount,

        @NotBlank(message = "Recipient is required")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{5,40}",
                message = "Recipient must contain 5 to 40 letters, digits, underscores or hyphens"
        )
        String recipient,

        @Pattern(
                regexp = "\\d{10,15}",
                message = "Customer phone must contain 10 to 15 digits"
        )
        String customerPhone,

        @Pattern(
                regexp = "(?i)prepaid|postpaid|change|renew",
                message = "Invalid service option"
        )
        String option,

        @NotBlank(message = "Transaction PIN is required")
        @Pattern(
                regexp = "\\d{6}",
                message = "Transaction PIN must be exactly 6 digits"
        )
        String transactionPin
) {
}
