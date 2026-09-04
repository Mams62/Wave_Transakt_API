package com.wavetransakt.wallet.service;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public Wallet createWallet(User user) {

        if (walletRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalArgumentException(
                    "User already has a wallet"
            );
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .walletNumber(generateWalletNumber())
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .build();

        return walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {

        Wallet wallet = walletRepository
                .findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet not found"
                        )
                );

        return WalletResponse.builder()
                .id(wallet.getId())
                .walletNumber(wallet.getWalletNumber())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .build();
    }

    @Transactional
    public WalletResponse fundWallet(
            UUID userId,
            BigDecimal amount
    ) {

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Funding amount must be greater than zero"
            );
        }

        Wallet wallet = walletRepository
                .findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet not found"
                        )
                );

        if (wallet.getStatus() != WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Wallet is not active"
            );
        }

        wallet.setBalance(
                wallet.getBalance().add(amount)
        );

        Wallet savedWallet =
                walletRepository.save(wallet);

        return WalletResponse.builder()
                .id(savedWallet.getId())
                .walletNumber(
                        savedWallet.getWalletNumber()
                )
                .balance(
                        savedWallet.getBalance()
                )
                .currency(
                        savedWallet.getCurrency()
                )
                .status(
                        savedWallet.getStatus().name()
                )
                .build();
    }

    private String generateWalletNumber() {

        String walletNumber;

        do {
            walletNumber = String.valueOf(
                    ThreadLocalRandom.current()
                            .nextLong(
                                    1_000_000_000L,
                                    10_000_000_000L
                            )
            );

        } while (
                walletRepository.existsByWalletNumber(
                        walletNumber
                )
        );

        return walletNumber;
    }
}