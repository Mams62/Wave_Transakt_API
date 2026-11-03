package com.wavetransakt.partner.sandbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerSandboxIntentRepository extends JpaRepository<PartnerSandboxIntent, UUID> {
    Optional<PartnerSandboxIntent> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
    Optional<PartnerSandboxIntent> findByActorIdAndReference(UUID actorId, String reference);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PartnerSandboxIntent i where i.reference = :reference")
    Optional<PartnerSandboxIntent> findByReferenceForUpdate(@Param("reference") String reference);
    long countByState(PartnerSandboxState state);
    List<PartnerSandboxIntent> findByStateInOrderByCreatedAtAsc(List<PartnerSandboxState> states, Pageable pageable);

    /** PostgreSQL unique index resolves races across API replicas. */
    @Modifying
    @Query(value = """
            INSERT INTO partner_sandbox_intents
                (id, actor_id, reference, idempotency_key, request_hash, amount_kobo,
                 merchant_reference, state, created_at, updated_at, version)
            VALUES (:id, :actorId, :reference, :key, :hash, :amount, :merchantRef,
                    'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            ON CONFLICT (actor_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int reserve(@Param("id") UUID id,
                @Param("actorId") UUID actorId,
                @Param("reference") String reference,
                @Param("key") String key,
                @Param("hash") String hash,
                @Param("amount") long amountKobo,
                @Param("merchantRef") String merchantReference);
}
