package com.wavetransakt.wallet.service;

import com.wavetransakt.common.NigerianPhoneNumber;
import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    /**
     * Provider used for newly created Wave wallet records. The Wave wallet
     * number remains our stable customer-facing identifier while the provider
     * supplies regulated wallet infrastructure behind the provider boundary.
     */
    @Value("${wave.wallet.default-provider:INTERSWITCH}")
    private String defaultProvider = "INTERSWITCH";

    /**
     * Creates the stable internal Wave wallet record. Provider onboarding is a
     * separate step and must not be treated as complete merely because the
     * local wallet row exists.
     */
    @Transactional
    public Wallet createWallet(User user) {
        if (walletRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalArgumentException("User already has a wallet");
        }

        String walletNumber = NigerianPhoneNumber.toLocal(user.getPhone());

        if (walletRepository.existsByWalletNumber(walletNumber)) {
            throw new IllegalArgumentException("Phone number is already linked to another Wave wallet");
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .walletNumber(walletNumber)
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .provider(resolveDefaultProvider())
                .build();

        Wallet savedWallet = walletRepository.save(wallet);
        ledgerService.ensureWalletAccount(savedWallet);
        return savedWallet;
    }

    private String resolveDefaultProvider() {
        if (defaultProvider == null || defaultProvider.isBlank()) {
            return "INTERSWITCH";
        }
        return defaultProvider.trim().toUpperCase(Locale.ROOT);
    }
}
