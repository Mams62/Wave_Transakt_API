package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.PosTerminal;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface PosTerminalLookupRepository extends Repository<PosTerminal, UUID> {
    Optional<PosTerminal> findByTerminalCode(String terminalCode);
}
