package com.wavetransakt.payment.repository;

import com.wavetransakt.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEvent, UUID> {

    /**
     * Atomically reserve a webhook event.
     *
     * If Paystack sends the exact same event more than once,
     * only the first transaction receives a result of 1.
     *
     * Later deliveries receive 0 and are safely ignored.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO payment_webhook_events (
                        provider,
                        event_key,
                        event_type,
                        reference,
                        provider_transaction_id,
                        payload_hash,
                        status,
                        created_at
                    )
                    VALUES (
                        'PAYSTACK',
                        :eventKey,
                        :eventType,
                        :reference,
                        :providerTransactionId,
                        :payloadHash,
                        'RECEIVED',
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (event_key)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("eventKey")
            String eventKey,

            @Param("eventType")
            String eventType,

            @Param("reference")
            String reference,

            @Param("providerTransactionId")
            String providerTransactionId,

            @Param("payloadHash")
            String payloadHash
    );

    @Modifying
    @Query(
            value = """
                    UPDATE payment_webhook_events
                    SET
                        status = 'PROCESSED',
                        processed_at = CURRENT_TIMESTAMP
                    WHERE event_key = :eventKey
                    """,
            nativeQuery = true
    )
    int markProcessed(
            @Param("eventKey")
            String eventKey
    );
}