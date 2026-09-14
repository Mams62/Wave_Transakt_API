package com.wavetransakt.card.repository;

import com.wavetransakt.card.entity.WaveCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaveCardRepository extends JpaRepository<WaveCard, UUID> {

    @EntityGraph(attributePaths = {"user", "wallet"})
    List<WaveCard> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"user", "wallet"})
    Optional<WaveCard> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndStatusIn(UUID userId, List<String> statuses);
}
