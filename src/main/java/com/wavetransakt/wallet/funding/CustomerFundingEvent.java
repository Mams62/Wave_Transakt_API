package com.wavetransakt.wallet.funding;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_funding_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_customer_funding_event_provider_event", columnNames = {"provider_code", "provider_event_id"})
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFundingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funding_account_id")
    private CustomerFundingAccount fundingAccount;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    @Column(name = "provider_event_id", nullable = false, length = 160)
    private String providerEventId;

    @Column(name = "provider_reference", length = 160)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CustomerFundingEventStatus status;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "credited_at")
    private LocalDateTime creditedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
