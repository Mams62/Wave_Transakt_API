package com.wavetransakt.ledger.repository;

import com.wavetransakt.ledger.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByReference(
            String reference
    );

    boolean existsByReference(
            String reference
    );
}