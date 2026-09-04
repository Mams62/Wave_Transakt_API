package com.wavetransakt.qr.repository;

import com.wavetransakt.qr.entity.PaymentQrIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentQrIntentRepository
        extends JpaRepository<PaymentQrIntent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentQrIntent p
            where p.id = :id
            """)
    Optional<PaymentQrIntent> findByIdForUpdate(
            @Param("id") UUID id
    );
}