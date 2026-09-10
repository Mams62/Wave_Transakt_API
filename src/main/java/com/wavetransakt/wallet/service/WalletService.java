package com.wavetransakt.wallet.service;

import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    /**
     * Create the stable internal Wave wallet record. The external Wema NUBAN is
     * created separately after the user completes Wema's NIN + OTP flow.
     */
    @Transactional
    public Wallet createWallet(User user) {
        if (walletRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalArgumentException("User already has a wallet");
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .walletNumber(generateWalletNumber())
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .build();

        Wallet savedWallet = walletRepository.save(wallet);
        ledgerService.ensureWalletAccount(savedWallet);
        return savedWallet;
    }

    /**
     * No direct funding method exists. Wallet value must come from the bank or
     * another verified provider event; the server must never mint balance from a
     * client request.
     */
    private String generateWalletNumber() {
        String walletNumber;
        do {
            walletNumber = String.valueOf(
                    ThreadLocalRandom.current().nextLong(
                            1_000_000_000L,
                            10_000_000_000L
                    )
            );
        } while (walletRepository.existsByWalletNumber(walletNumber));
        return walletNumber;
    }
}
