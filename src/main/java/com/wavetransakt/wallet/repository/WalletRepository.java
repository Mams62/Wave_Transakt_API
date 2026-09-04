package com.wavetransakt.wallet.repository;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository
        extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    Optional<Wallet> findByWalletNumber(String walletNumber);

    boolean existsByWalletNumber(String walletNumber);

    /**
     * Financial operations must lock wallet rows before
     * checking or changing balances.
     *
     * PESSIMISTIC_WRITE prevents another transaction from
     * modifying the same wallet until the current DB
     * transaction completes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w
            from Wallet w
            where w.id = :walletId
            """)
    Optional<Wallet> findByIdForUpdate(
            @Param("walletId") UUID walletId
    );
}