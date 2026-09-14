package com.wavetransakt.card.entity;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wave_cards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaveCard {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_card_reference", nullable = false, unique = true, length = 160)
    private String providerCardReference;

    @Column(name = "masked_display_reference", length = 80)
    private String maskedDisplayReference;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "contactless_enabled", nullable = false)
    @Builder.Default
    private boolean contactlessEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
