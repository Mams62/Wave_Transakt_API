package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.PosTerminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosTerminalRepository extends JpaRepository<PosTerminal, UUID> {
    List<PosTerminal> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
