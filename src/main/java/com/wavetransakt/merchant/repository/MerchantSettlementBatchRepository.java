package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantSettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantSettlementBatchRepository extends JpaRepository<MerchantSettlementBatch, UUID> {
    List<MerchantSettlementBatch> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
