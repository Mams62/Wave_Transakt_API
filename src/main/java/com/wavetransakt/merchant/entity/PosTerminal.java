package com.wavetransakt.merchant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pos_terminals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosTerminal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "terminal_code", nullable = false, unique = true, length = 60)
    private String terminalCode;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Column(name = "provider_terminal_id", length = 120)
    private String providerTerminalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private PosTerminalStatus status = PosTerminalStatus.PENDING_PROVIDER_LINK;

    @Column(name = "supports_qr", nullable = false)
    @Builder.Default
    private boolean supportsQr = true;

    @Column(name = "supports_nfc", nullable = false)
    @Builder.Default
    private boolean supportsNfc = false;

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
