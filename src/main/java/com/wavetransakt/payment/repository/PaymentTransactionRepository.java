package com.wavetransakt.payment.repository;

import com.wavetransakt.payment.entity.PaymentTransaction;
import com.wavetransakt.payment.entity.PaymentTransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByReference(
            String reference
    );

    boolean existsByReference(
            String reference
    );

    Optional<PaymentTransaction>
    findByProviderTransactionId(
            String providerTransactionId
    );

    Optional<PaymentTransaction>
    findByReferenceAndStatus(
            String reference,
            PaymentTransactionStatus status
    );

    /**
     * Critical funding lock.
     *
     * Concurrent verification requests for the same
     * Paystack payment must serialize here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentTransaction p
            where p.reference = :reference
            """)
    Optional<PaymentTransaction> findByReferenceForUpdate(
            @Param("reference")
            String reference
    );
}