package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.PosTerminalSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosTerminalSessionRepository extends JpaRepository<PosTerminalSession, UUID> {
    /**
     * POS session endpoints need the terminal, merchant and owner after the session
     * validation transaction returns. Fetch that ownership chain with the session so
     * controller-side owner scoping never dereferences a detached Hibernate proxy.
     */
    @EntityGraph(attributePaths = {"terminal", "terminal.merchant", "terminal.merchant.owner"})
    Optional<PosTerminalSession> findByTokenHash(String tokenHash);

    List<PosTerminalSession> findAllByTerminalId(UUID terminalId);
}
