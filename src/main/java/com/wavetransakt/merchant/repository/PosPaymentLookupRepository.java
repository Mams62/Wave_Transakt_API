package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.MerchantPayment;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosPaymentLookupRepository extends Repository<MerchantPayment, UUID> {
    List<MerchantPayment> findAllByTerminalIdOrderByCreatedAtDesc(UUID terminalId);

    Optional<MerchantPayment> findByIdAndTerminalId(UUID paymentId, UUID terminalId);
}
