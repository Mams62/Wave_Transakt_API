package com.wavetransakt.wallet.service;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.dto.WemaWalletActionResponse;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WemaWalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import com.wavetransakt.wallet.wema.WemaWalletClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WemaWalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WemaWalletClient wemaWalletClient;

    @Transactional
    public WemaWalletActionResponse startOnboarding(UUID userId) {
        User user = requireUser(userId);
        Wallet wallet = requireWallet(userId);

        if (wallet.getProviderAccountNumber() != null &&
                !wallet.getProviderAccountNumber().isBlank()) {
            wallet.setProviderStatus(WemaWalletStatus.ACTIVE);
            return response(wallet, "Wema wallet is already active");
        }

        if (wallet.getProviderStatus() == WemaWalletStatus.OTP_REQUIRED &&
                wallet.getProviderTrackingId() != null &&
                !wallet.getProviderTrackingId().isBlank()) {
            return response(
                    wallet,
                    "A Wema OTP has already been requested. Enter the OTP sent to the phone number linked to the NIN."
            );
        }

        WemaWalletClient.StartResult result = wemaWalletClient.startNinWallet(user);

        wallet.setProvider("WEMA");
        wallet.setProviderTrackingId(result.trackingId());
        wallet.setProviderStatus(WemaWalletStatus.OTP_REQUIRED);
        wallet.setProviderMessage(result.message());
        wallet.setProviderLastSyncedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        return response(wallet, result.message());
    }

    @Transactional
    public WemaWalletActionResponse verifyOtp(UUID userId, String otp) {
        User user = requireUser(userId);
        Wallet wallet = requireWallet(userId);

        if (wallet.getProviderStatus() != WemaWalletStatus.OTP_REQUIRED ||
                wallet.getProviderTrackingId() == null ||
                wallet.getProviderTrackingId().isBlank()) {
            throw new IllegalArgumentException(
                    "Start Wema wallet onboarding before submitting an OTP"
            );
        }

        WemaWalletClient.ProviderMessage result = wemaWalletClient.validateNinOtp(
                user.getPhone(),
                wallet.getProviderTrackingId(),
                otp
        );

        wallet.setProviderStatus(WemaWalletStatus.PENDING);
        wallet.setProviderMessage(result.message());
        wallet.setProviderLastSyncedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        return response(wallet, result.message());
    }

    @Transactional
    public WemaWalletActionResponse refreshOnboarding(UUID userId) {
        User user = requireUser(userId);
        Wallet wallet = requireWallet(userId);

        if (wallet.getProviderAccountNumber() != null &&
                !wallet.getProviderAccountNumber().isBlank()) {
            wallet.setProviderStatus(WemaWalletStatus.ACTIVE);
            wallet.setProviderLastSyncedAt(LocalDateTime.now());
            walletRepository.save(wallet);
            return response(wallet, "Wema wallet is active");
        }

        if (wallet.getProviderStatus() == WemaWalletStatus.NOT_STARTED) {
            return response(wallet, "Wema wallet onboarding has not started");
        }

        WemaWalletClient.PartnershipAccountDetails details =
                wemaWalletClient.getPartnershipAccountDetails(user.getPhone());

        if (details.accountNumber() == null || details.accountNumber().isBlank()) {
            wallet.setProviderStatus(WemaWalletStatus.PENDING);
            wallet.setProviderMessage(details.message());
        } else {
            String accountNumber = details.accountNumber().trim();
            if (!accountNumber.matches("\\d{10}")) {
                throw new IllegalStateException(
                        "Wema returned an invalid wallet account number"
                );
            }

            wallet.setProviderAccountNumber(accountNumber);
            wallet.setProviderStatus(WemaWalletStatus.ACTIVE);
            wallet.setProviderMessage(details.message());
        }

        wallet.setProviderLastSyncedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        return response(wallet, wallet.getProviderMessage());
    }

    /**
     * User-facing wallet response. Wema is the balance source once a provider
     * account has been created. A provider failure is never silently presented
     * as a fresh zero balance.
     */
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = requireWallet(userId);

        BigDecimal visibleBalance = wallet.getBalance();
        boolean balanceFresh = false;
        String providerMessage = wallet.getProviderMessage();
        String providerStatus = wallet.getProviderStatus().name();

        String accountNumber = wallet.getProviderAccountNumber();
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                WemaWalletClient.WalletAccountDetails details =
                        wemaWalletClient.getWalletDetails(accountNumber);
                visibleBalance = details.availableBalance();
                balanceFresh = true;
                if (details.walletStatus() != null && !details.walletStatus().isBlank()) {
                    providerStatus = details.walletStatus().trim();
                }
            } catch (Exception e) {
                providerMessage = "Wema balance is temporarily unavailable. Pull to refresh before making a payment.";
            }
        }

        return WalletResponse.builder()
                .id(wallet.getId())
                .walletNumber(wallet.getWalletNumber())
                .balance(visibleBalance)
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .provider("WEMA")
                .bankName("Wema Bank")
                .accountNumber(accountNumber)
                .providerStatus(providerStatus)
                .providerMessage(providerMessage)
                .onboardingRequired(wallet.getProviderStatus() != WemaWalletStatus.ACTIVE)
                .balanceFresh(balanceFresh)
                .build();
    }

    private User requireUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Wallet requireWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    private WemaWalletActionResponse response(Wallet wallet, String message) {
        return new WemaWalletActionResponse(
                wallet.getProviderStatus().name(),
                message,
                wallet.getProviderAccountNumber()
        );
    }
}
