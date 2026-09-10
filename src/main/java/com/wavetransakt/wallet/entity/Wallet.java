package com.wavetransakt.wallet.entity;

import com.wavetransakt.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Stable Wave Transakt internal wallet identifier.
     *
     * This value is intentionally kept separate from the bank account number so
     * QR/payment addresses do not change if the external provider changes.
     */
    @Column(nullable = false, unique = true, length = 20)
    private String walletNumber;

    /**
     * Local accounting projection. Once the Wema wallet is active, user-facing
     * balance is refreshed from Wema and this value must not be treated as an
     * independent source of funds.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String provider = "WEMA";

    /**
     * Wema/ALAT 10-digit NUBAN assigned after wallet creation completes.
     */
    @Column(name = "provider_account_number", unique = true, length = 20)
    private String providerAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status", nullable = false, length = 30)
    @Builder.Default
    private WemaWalletStatus providerStatus = WemaWalletStatus.NOT_STARTED;

    /**
     * Tracking ID returned during Wema NIN/BVN OTP onboarding.
     */
    @Column(name = "provider_tracking_id", length = 120)
    private String providerTrackingId;

    @Column(name = "provider_message", length = 255)
    private String providerMessage;

    @Column(name = "provider_last_synced_at")
    private LocalDateTime providerLastSyncedAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
