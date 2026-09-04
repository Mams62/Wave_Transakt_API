package com.wavetransakt.payment.entity;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(
                        name = "idx_payment_transactions_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_payment_transactions_wallet",
                        columnList = "wallet_id"
                ),
                @Index(
                        name = "idx_payment_transactions_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "wallet_id",
            nullable = false
    )
    private Wallet wallet;

    @Column(
            nullable = false,
            unique = true,
            length = 100
    )
    private String reference;

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

    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private String provider = "PAYSTACK";

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private PaymentTransactionStatus status =
            PaymentTransactionStatus.PENDING;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt =
            LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt =
            LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
