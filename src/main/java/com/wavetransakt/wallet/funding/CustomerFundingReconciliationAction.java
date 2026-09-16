package com.wavetransakt.wallet.funding;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_funding_reconciliation_actions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFundingReconciliationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "funding_event_id", nullable = false)
    private UUID fundingEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private FundingReconciliationActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 80)
    private FundingReconciliationReasonCode reasonCode;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
