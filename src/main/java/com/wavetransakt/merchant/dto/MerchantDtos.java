package com.wavetransakt.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class MerchantDtos {

    private MerchantDtos() {
    }

    public record CreateMerchantRequest(
            @NotBlank @Size(max = 160) String businessName,
            @NotBlank @Size(max = 60) String businessType
    ) {
    }

    public record ResolveMerchantQrRequest(
            @NotBlank @Size(max = 160) String payload
    ) {
    }

    public record MerchantResponse(
            UUID id,
            String merchantCode,
            String businessName,
            String businessType,
            String status,
            String providerCode,
            boolean providerLinked,
            LocalDateTime createdAt
    ) {
    }

    public record MerchantQrResponse(
            UUID merchantId,
            String publicId,
            String qrPayload,
            boolean active
    ) {
    }

    public record MerchantQrResolveResponse(
            UUID merchantId,
            String merchantCode,
            String businessName,
            String businessType,
            String status,
            boolean payable
    ) {
    }

    public record PosTerminalResponse(
            UUID id,
            String terminalCode,
            String status,
            String providerCode,
            boolean providerLinked,
            boolean supportsQr,
            boolean supportsNfc,
            LocalDateTime createdAt
    ) {
    }

    public record MerchantPaymentResponse(
            UUID id,
            String paymentReference,
            String channel,
            BigDecimal amount,
            String currency,
            String status,
            String receiptNumber,
            String settlementStatus,
            String providerCode,
            LocalDateTime createdAt
    ) {
    }

    public record SettlementBatchResponse(
            UUID id,
            String providerCode,
            String currency,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            Integer paymentCount,
            String status,
            LocalDate settlementDate,
            LocalDateTime createdAt
    ) {
    }

    public record ReconciliationItemResponse(
            UUID id,
            UUID paymentId,
            String paymentReference,
            BigDecimal expectedAmount,
            BigDecimal providerAmount,
            BigDecimal differenceAmount,
            String status,
            String reason,
            LocalDateTime createdAt
    ) {
    }
}
