package com.wavetransakt.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class WemaTransferDtos {

    private WemaTransferDtos() {
    }

    public record Bank(
            String bankCode,
            String bankName
    ) {
    }

    public record NameEnquiry(
            String bankCode,
            String bankName,
            String accountNumber,
            String accountName,
            String sessionId,
            String message
    ) {
    }

    public record NipCharge(
            String description,
            BigDecimal minimumAmount,
            BigDecimal maximumAmount,
            BigDecimal charge
    ) {
    }

    public record TransferRequest(
            @NotBlank(message = "Destination bank code is required")
            @Size(max = 20, message = "Destination bank code is too long")
            String destinationBankCode,

            @NotBlank(message = "Destination account number is required")
            @Pattern(regexp = "\\d{10}", message = "Destination account number must be exactly 10 digits")
            String destinationAccountNumber,

            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "Amount must be at least NGN 1.00")
            BigDecimal amount,

            @Size(max = 80, message = "Narration must not exceed 80 characters")
            String narration,

            @NotBlank(message = "Transaction PIN is required")
            @Pattern(regexp = "\\d{6}", message = "Transaction PIN must be exactly 6 digits")
            String transactionPin
    ) {
    }

    public record TransferResponse(
            String transactionReference,
            String providerReference,
            String status,
            String message,
            String sourceAccountNumber,
            String destinationBankCode,
            String destinationBankName,
            String destinationAccountNumber,
            String destinationAccountName,
            BigDecimal amount,
            String currency
    ) {
    }

    public record AuthorizationCallbackRequest(
            String securityInfo,
            String transactionReference
    ) {
    }

    public record AuthorizationCallbackResponse(
            String transactionReference,
            boolean authorized
    ) {
    }

    public record BanksResponse(List<Bank> banks) {
    }

    public record NipChargesResponse(List<NipCharge> charges) {
    }
}
