package com.wavetransakt.payment.repository;

import com.wavetransakt.payment.entity.PaymentTransaction;
import com.wavetransakt.payment.entity.PaymentTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByReference(
            String reference
    );

    boolean existsByReference(String reference);

    Optional<PaymentTransaction>
    findByProviderTransactionId(
            String providerTransactionId
    );

    Optional<PaymentTransaction>
    findByReferenceAndStatus(
            String reference,
            PaymentTransactionStatus status
    );
}
