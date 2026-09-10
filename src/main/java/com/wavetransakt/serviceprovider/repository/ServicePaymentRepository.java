package com.wavetransakt.serviceprovider.repository;

import com.wavetransakt.serviceprovider.entity.ServicePayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServicePaymentRepository extends JpaRepository<ServicePayment, UUID> {

    Optional<ServicePayment> findByReference(String reference);

    Optional<ServicePayment> findByWalletIdAndIdempotencyKey(
            UUID walletId,
            String idempotencyKey
    );

    Optional<ServicePayment> findByWalletIdAndReference(
            UUID walletId,
            String reference
    );

    Optional<ServicePayment> findByProviderRequestId(String providerRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from ServicePayment p
            where p.id = :paymentId
            """)
    Optional<ServicePayment> findByIdForUpdate(
            @Param("paymentId") UUID paymentId
    );
}
