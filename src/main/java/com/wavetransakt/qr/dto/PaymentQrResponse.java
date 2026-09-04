package com.wavetransakt.qr.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrResponse {

    private UUID intentId;

    private String qrType;

    private String payload;

    private String walletNumber;

    private String accountName;

    private BigDecimal amount;

    private String currency;

    private String description;

    private String status;

    private LocalDateTime expiresAt;
}