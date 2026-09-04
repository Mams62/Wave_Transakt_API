package com.wavetransakt.qr.entity;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_qr_intents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrIntent {

    @Id
    private UUID id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "recipient_wallet_id",
            nullable = false
    )
    private Wallet recipientWallet;

    @Column(
            nullable = false,
            unique = true,
            length = 64
    )
    private String nonce;

    @Column(
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Column(
            nullable = false,
            length = 3
    )
    @Builder.Default
    private String currency = "NGN";

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private PaymentQrStatus status =
            PaymentQrStatus.ACTIVE;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(
            name = "consumed_idempotency_key",
            length = 128
    )
    private String consumedIdempotencyKey;

    @Column(
            name = "transaction_reference",
            length = 50
    )
    private String transactionReference;

    @Column(
            name = "created_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime createdAt =
            LocalDateTime.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt =
            LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}