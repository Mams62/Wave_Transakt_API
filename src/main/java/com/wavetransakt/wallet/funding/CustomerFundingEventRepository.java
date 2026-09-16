package com.wavetransakt.wallet.funding;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerFundingEventRepository extends JpaRepository<CustomerFundingEvent, UUID> {

    boolean existsByProviderCodeAndProviderEventId(String providerCode, String providerEventId);

    Optional<CustomerFundingEvent> findByProviderCodeAndProviderEventId(String providerCode, String providerEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CustomerFundingEvent e where e.id = :eventId")
    Optional<CustomerFundingEvent> findByIdForUpdate(@Param("eventId") UUID eventId);
}
