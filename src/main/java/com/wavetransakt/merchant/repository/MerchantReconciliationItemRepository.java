package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantReconciliationItemRepository extends JpaRepository<MerchantReconciliationItem, UUID> {
    List<MerchantReconciliationItem> findAllBySettlementBatchIdOrderByCreatedAtAsc(UUID settlementBatchId);
}
