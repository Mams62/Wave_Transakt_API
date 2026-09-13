package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantQrAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantQrAddressRepository extends JpaRepository<MerchantQrAddress, UUID> {
    Optional<MerchantQrAddress> findByMerchantId(UUID merchantId);
    Optional<MerchantQrAddress> findByPublicIdAndActiveTrue(String publicId);
}
