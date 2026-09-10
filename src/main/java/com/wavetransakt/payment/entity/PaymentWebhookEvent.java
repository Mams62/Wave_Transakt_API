package com.wavetransakt.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_payment_webhook_events_event_key",
                        columnNames = "event_key"
                )
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            nullable = false,
            length = 30
    )
    private String provider;

    @Column(
            name = "event_key",
            nullable = false,
            unique = true,
            length = 180
    )
    private String eventKey;

    @Column(
            name = "event_type",
            nullable = false,
            length = 60
    )
    private String eventType;

    @Column(
            nullable = false,
            length = 100
    )
    private String reference;

    @Column(
            name = "provider_transaction_id",
            nullable = false,
            length = 100
    )
    private String providerTransactionId;

    @Column(
            name = "payload_hash",
            nullable = false,
            length = 64
    )
    private String payloadHash;

    @Column(
            nullable = false,
            length = 20
    )
    private String status;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "processed_at"
    )
    private LocalDateTime processedAt;
}