package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantPaymentRepository extends JpaRepository<MerchantPayment, UUID> {
    List<MerchantPayment> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
