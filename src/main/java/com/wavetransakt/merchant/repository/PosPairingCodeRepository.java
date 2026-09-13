package com.wavetransakt.merchant.repository;

import com.wavetransakt.merchant.entity.PosPairingCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PosPairingCodeRepository extends JpaRepository<PosPairingCode, UUID> {
    Optional<PosPairingCode> findByCodeHash(String codeHash);
}
