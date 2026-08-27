package com.wavetransakt.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        String reference,
        BigDecimal amount,
        String status,
        String authorizationUrl,
        BigDecimal walletBalance
) {}
