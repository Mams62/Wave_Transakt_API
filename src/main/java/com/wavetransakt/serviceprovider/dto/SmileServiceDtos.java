package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

public final class SmileServiceDtos {

    private SmileServiceDtos() {
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "Smile email is required")
            @Email(message = "Smile email must be valid")
            String email
    ) {
    }

    public record Account(
            String accountId,
            String friendlyName
    ) {
    }

    public record VerifyEmailResponse(
            String provider,
            String serviceId,
            String email,
            String customerName,
            List<Account> accounts,
            boolean valid,
            String message
    ) {
    }

    public record PurchaseRequest(
            @NotBlank(message = "Smile email is required")
            @Email(message = "Smile email must be valid")
            String email,

            @NotBlank(message = "Smile account ID is required")
            @Pattern(
                    regexp = "[A-Za-z0-9_-]{5,40}",
                    message = "Invalid Smile account ID"
            )
            String accountId,

            @NotBlank(message = "Smile plan variation is required")
            @Pattern(
                    regexp = "[A-Za-z0-9_-]{1,80}",
                    message = "Invalid Smile plan variation"
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
