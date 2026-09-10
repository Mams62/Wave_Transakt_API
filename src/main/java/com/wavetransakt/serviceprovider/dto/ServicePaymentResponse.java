package com.wavetransakt.serviceprovider.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ServicePaymentResponse(
        UUID id,
        String reference,
        String providerRequestId,
        String serviceKind,
        String serviceId,
        String serviceName,
        String variationCode,
        String recipient,
        BigDecimal amount,
        String currency,
        String status,
        String providerStatus,
        String providerTransactionId,
        String message,
        LocalDateTime createdAt
) {
}
