package com.wavetransakt.repository;

import com.wavetransakt.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    Optional<WalletTransaction> findByReference(String reference);

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
