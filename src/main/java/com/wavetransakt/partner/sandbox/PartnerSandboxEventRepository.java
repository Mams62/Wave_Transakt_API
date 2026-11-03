package com.wavetransakt.partner.sandbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PartnerSandboxEventRepository extends JpaRepository<PartnerSandboxEvent, UUID> {
    Optional<PartnerSandboxEvent> findByEventKey(String eventKey);
    long countByReconciliationOutcome(String outcome);

    @Modifying
    @Query(value = """
            INSERT INTO partner_sandbox_events
                (id, event_key, intent_reference, payload_hash, claimed_state,
                 reconciliation_outcome, received_at)
            VALUES (:id, :eventKey, :ref, :hash, :claimedState, 'RECEIVED', CURRENT_TIMESTAMP)
            ON CONFLICT (event_key) DO NOTHING
            """, nativeQuery = true)
    int reserve(@Param("id") UUID id,
                @Param("eventKey") String eventKey,
                @Param("ref") String ref,
                @Param("hash") String hash,
                @Param("claimedState") String claimedState);
}
