package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.PosTerminalSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PosTerminalSessionRepository extends JpaRepository<PosTerminalSession, UUID> {
    Optional<PosTerminalSession> findByTokenHash(String tokenHash);
}
