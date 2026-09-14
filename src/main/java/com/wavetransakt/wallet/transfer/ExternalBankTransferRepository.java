package com.wavetransakt.wallet.transfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExternalBankTransferRepository extends JpaRepository<ExternalBankTransfer, UUID> {

    Optional<ExternalBankTransfer> findByReference(String reference);

    Optional<ExternalBankTransfer> findByWalletIdAndReference(UUID walletId, String reference);

    Optional<ExternalBankTransfer> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ExternalBankTransfer t where t.id = :id")
    Optional<ExternalBankTransfer> findByIdForUpdate(@Param("id") UUID id);
}
