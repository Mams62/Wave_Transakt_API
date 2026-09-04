package com.wavetransakt.wallet.repository;

import com.wavetransakt.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByReference(String reference);

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(
            UUID walletId,
            Pageable pageable
    );
}