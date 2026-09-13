package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantProviderEventRepository extends JpaRepository<MerchantProviderEvent, UUID> {
    Optional<MerchantProviderEvent> findByEventKey(String eventKey);
    boolean existsByEventKey(String eventKey);
}
