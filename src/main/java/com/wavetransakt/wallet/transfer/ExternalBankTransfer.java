package com.wavetransakt.wallet.transfer;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "external_bank_transfers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_external_bank_transfer_wallet_idempotency",
                        columnNames = {"wallet_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_external_bank_transfer_reference", columnList = "reference"),
                @Index(name = "idx_external_bank_transfer_wallet", columnList = "wallet_id"),
                @Index(name = "idx_external_bank_transfer_provider_reference", columnList = "provider_reference"),
                @Index(name = "idx_external_bank_transfer_created", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBankTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 70)
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
    private String provider = "INTERSWITCH";

    @Column(name = "destination_bank_code", nullable = false, length = 20)
    private String destinationBankCode;

    @Column(name = "destination_account_number", nullable = false, length = 10)
    private String destinationAccountNumber;

    @Column(name = "destination_account_name", nullable = false, length = 160)
    private String destinationAccountName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Column(length = 160)
    private String narration;

    @Column(name = "provider_transfer_code", length = 80)
    private String providerTransferCode;

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Column(name = "provider_response_code", length = 40)
    private String providerResponseCode;

    @Column(name = "provider_message", length = 255)
    private String providerMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExternalBankTransferStatus status = ExternalBankTransferStatus.RESERVED;

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
