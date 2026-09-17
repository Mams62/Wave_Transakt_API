package com.wavetransakt.identity.repository;

import com.wavetransakt.identity.entity.LivenessSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LivenessSessionRepository extends JpaRepository<LivenessSession, UUID> {
    Optional<LivenessSession> findByProviderSessionId(String providerSessionId);
    Optional<LivenessSession> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Serializes expensive capture/provider work for one liveness session across
     * concurrent requests and across multiple API instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from LivenessSession session where session.id = :id")
    Optional<LivenessSession> findByIdForUpdate(@Param("id") UUID id);
}
