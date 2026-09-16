package com.wavetransakt.wallet.funding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerFundingEventRepository extends JpaRepository<CustomerFundingEvent, UUID> {

    boolean existsByProviderCodeAndProviderEventId(String providerCode, String providerEventId);

    Optional<CustomerFundingEvent> findByProviderCodeAndProviderEventId(String providerCode, String providerEventId);
}
