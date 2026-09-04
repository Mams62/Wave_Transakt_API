package com.wavetransakt.qr.entity;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "wallet_qr_addresses",
        indexes = {
                @Index(
                        name = "idx_wallet_qr_wallet_id",
                        columnList = "wallet_id"
                ),
                @Index(
                        name = "idx_wallet_qr_code",
                        columnList = "qr_code"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletQrAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "wallet_id",
            nullable = false,
            unique = true
    )
    private Wallet wallet;

    @Column(
            name = "qr_code",
            nullable = false,
            unique = true,
            length = 100
    )
    private String qrCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}