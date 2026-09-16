package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MerchantProviderEventRepository extends JpaRepository<MerchantProviderEvent, UUID> {

    Optional<MerchantProviderEvent> findByEventKey(String eventKey);

    /**
     * Atomically reserves a provider event or rejected-attempt audit record.
     *
     * PostgreSQL owns the replay decision through the event_key uniqueness
     * constraint, so two API instances receiving the same callback cannot both
     * reserve it. The caller must verify provider authenticity before using a
     * trusted provider event ID as eventKey.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO merchant_provider_events (
                        id,
                        provider_code,
                        event_key,
                        event_type,
                        provider_reference,
                        payment_reference,
                        payload_hash,
                        verification_status,
                        processing_status,
                        received_at,
                        processed_at
                    )
                    VALUES (
                        gen_random_uuid(),
                        :providerCode,
                        :eventKey,
                        :eventType,
                        :providerReference,
                        NULL,
                        :payloadHash,
                        :verificationStatus,
                        :processingStatus,
                        CURRENT_TIMESTAMP,
                        :processedAt
                    )
                    ON CONFLICT (event_key)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("providerCode") String providerCode,
            @Param("eventKey") String eventKey,
            @Param("eventType") String eventType,
            @Param("providerReference") String providerReference,
            @Param("payloadHash") String payloadHash,
            @Param("verificationStatus") String verificationStatus,
            @Param("processingStatus") String processingStatus,
            @Param("processedAt") LocalDateTime processedAt
    );
}
