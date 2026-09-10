package com.wavetransakt.serviceprovider.entity;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "service_payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_service_payment_wallet_idempotency",
                        columnNames = {"wallet_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_service_payment_reference", columnList = "reference"),
                @Index(name = "idx_service_payment_wallet", columnList = "wallet_id"),
                @Index(name = "idx_service_payment_provider_request", columnList = "provider_request_id"),
                @Index(name = "idx_service_payment_created", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String provider = "VTPASS";

    @Column(name = "service_kind", nullable = false, length = 20)
    private String serviceKind;

    @Column(name = "service_id", nullable = false, length = 80)
    private String serviceId;

    @Column(name = "service_name", nullable = false, length = 140)
    private String serviceName;

    @Column(name = "variation_code", length = 120)
    private String variationCode;

    @Column(nullable = false, length = 40)
    private String recipient;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Column(name = "provider_request_id", nullable = false, unique = true, length = 100)
    private String providerRequestId;

    @Column(name = "provider_transaction_id", length = 120)
    private String providerTransactionId;

    @Column(name = "provider_status", length = 80)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ServicePaymentStatus status = ServicePaymentStatus.PENDING;

    @Column(name = "provider_message", length = 255)
    private String providerMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
