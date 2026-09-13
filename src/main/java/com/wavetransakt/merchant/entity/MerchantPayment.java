package com.wavetransakt.merchant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchant_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminal_id")
    private PosTerminal terminal;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 80)
    private String paymentReference;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Column(name = "provider_reference", length = 160)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MerchantPaymentChannel channel;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private MerchantPaymentStatus status = MerchantPaymentStatus.CREATED;

    @Column(name = "receipt_number", unique = true, length = 80)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 40)
    @Builder.Default
    private MerchantSettlementStatus settlementStatus = MerchantSettlementStatus.NOT_READY;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
