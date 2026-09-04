package com.wavetransakt.transaction.repository;

import com.wavetransakt.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(
            String reference
    );

    boolean existsByReference(
            String reference
    );

    Optional<Transaction>
    findBySenderWalletIdAndIdempotencyKey(
            UUID senderWalletId,
            String idempotencyKey
    );

    Page<Transaction> findBySenderWalletId(
            UUID senderWalletId,
            Pageable pageable
    );

    Page<Transaction> findByReceiverWalletId(
            UUID receiverWalletId,
            Pageable pageable
    );

    Page<Transaction>
    findBySenderWalletIdOrReceiverWalletId(
            UUID senderWalletId,
            UUID receiverWalletId,
            Pageable pageable
    );
}