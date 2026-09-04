package com.wavetransakt.qr.repository;

import com.wavetransakt.qr.entity.WalletQrAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletQrAddressRepository
        extends JpaRepository<WalletQrAddress, UUID> {

    Optional<WalletQrAddress> findByWalletId(UUID walletId);

    Optional<WalletQrAddress> findByQrCodeAndActiveTrue(
            String qrCode
    );

    boolean existsByQrCode(String qrCode);
}