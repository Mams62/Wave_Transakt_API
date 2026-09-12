package com.wavetransakt.identity.repository;

import com.wavetransakt.identity.entity.LivenessSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LivenessSessionRepository extends JpaRepository<LivenessSession, UUID> {
    Optional<LivenessSession> findByProviderSessionId(String providerSessionId);
    Optional<LivenessSession> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
}
