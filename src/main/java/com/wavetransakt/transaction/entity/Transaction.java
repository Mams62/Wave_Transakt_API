package com.wavetransakt.transaction.entity;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(
                        name = "idx_transaction_reference",
                        columnList = "reference"
                ),
                @Index(
                        name = "idx_transaction_sender",
                        columnList = "sender_wallet_id"
                ),
                @Index(
                        name = "idx_transaction_receiver",
                        columnList = "receiver_wallet_id"
                ),
                @Index(
                        name = "idx_transaction_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_transaction_idempotency_key",
                        columnList = "idempotency_key"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique Wave Transakt transaction reference.
     *
     * Example:
     * WT-20260905-123456
     */
    @Column(
            nullable = false,
            unique = true,
            length = 50
    )
    private String reference;

    /**
     * Idempotency key supplied by the client.
     *
     * The same key may be retried for the SAME transfer,
     * but must never be reused for another financial request.
     *
     * Existing transactions created before Stage 4 may
     * legitimately contain NULL here.
     */
    @Column(
            name = "idempotency_key",
            length = 128
    )
    private String idempotencyKey;

    /**
     * SHA-256 fingerprint of the normalized financial request.
     *
     * Used to detect an attempt to reuse the same
     * idempotency key with a different:
     *
     * - receiver
     * - amount
     * - description
     */
    @Column(
            name = "request_fingerprint",
            length = 64
    )
    private String requestFingerprint;

    /**
     * Wallet sending the money.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "sender_wallet_id",
            nullable = false
    )
    private Wallet senderWallet;

    /**
     * Wallet receiving the money.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "receiver_wallet_id",
            nullable = false
    )
    private Wallet receiverWallet;

    /**
     * Amount transferred.
     */
    @Column(
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    /**
     * ISO currency code.
     */
    @Column(
            nullable = false,
            length = 3
    )
    @Builder.Default
    private String currency = "NGN";

    /**
     * Transaction type.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private TransactionType type;

    /**
     * Transaction status.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private TransactionStatus status =
            TransactionStatus.PENDING;

    /**
     * Optional transaction description.
     */
    @Column(
            length = 255
    )
    private String description;

    /**
     * Time the transaction was created.
     */
    @Column(
            name = "created_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime createdAt =
            LocalDateTime.now();

    /**
     * Time the transaction was last updated.
     */
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