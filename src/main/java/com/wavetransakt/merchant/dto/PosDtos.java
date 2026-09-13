package com.wavetransakt.merchant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class PosDtos {

    private PosDtos() {
    }

    public record PosProfileResponse(
            UUID terminalId,
            String terminalCode,
            String terminalStatus,
            String merchantCode,
            String businessName,
            String businessType,
            String merchantStatus,
            boolean providerLinked,
            boolean qrEnabled,
            boolean cardEnabled,
            boolean nfcEnabled,
            boolean paymentAcceptanceEnabled,
            LocalDateTime createdAt
    ) {
    }

    public record PosQrResponse(
            String terminalCode,
            UUID merchantId,
            String merchantCode,
            String businessName,
            String publicId,
            String qrPayload,
            boolean active,
            boolean payable
    ) {
    }

    public record PosTransactionResponse(
            UUID paymentId,
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

    public record PosReceiptResponse(
            String receiptNumber,
            String paymentReference,
            String merchantCode,
            String businessName,
            String terminalCode,
            String channel,
            BigDecimal amount,
            String currency,
            String status,
            String settlementStatus,
            LocalDateTime paidAt
    ) {
    }

    public record PosSettlementResponse(
            UUID batchId,
            String providerCode,
            String providerBatchReference,
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
}
