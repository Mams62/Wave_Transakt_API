package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    List<Merchant> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<Merchant> findByIdAndOwnerId(UUID id, UUID ownerUserId);
    Optional<Merchant> findByMerchantCode(String merchantCode);
}
