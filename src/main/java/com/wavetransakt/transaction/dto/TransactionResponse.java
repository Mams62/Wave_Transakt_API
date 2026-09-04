package com.wavetransakt.transaction.dto;

import com.wavetransakt.transaction.entity.TransactionStatus;
import com.wavetransakt.transaction.entity.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private UUID id;
    private String reference;

    private String senderWalletNumber;
    private String receiverWalletNumber;

    private BigDecimal amount;
    private String currency;

    private TransactionType type;
    private TransactionStatus status;

    private String description;

    private LocalDateTime createdAt;
}
