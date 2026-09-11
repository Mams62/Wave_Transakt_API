package com.wavetransakt.wallet.service;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.dto.WemaDiagnosticsResponse;
import com.wavetransakt.wallet.dto.WemaWalletActionResponse;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WemaWalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import com.wavetransakt.wallet.wema.WemaProviderException;
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
            wallet.setProviderLastSyncedAt(LocalDateTime.now());
            walletRepository.save(wallet);
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

        if (wallet.getProviderStatus() == WemaWalletStatus.PENDING) {
            return response(
                    wallet,
                    "Wema wallet creation is already pending. Use Check Wema account status instead of starting again."
            );
        }

        requireProviderConfiguration();

        WemaWalletClient.StartResult result =
                wemaWalletClient.startNinWallet(user);

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

        requireProviderConfiguration();

        WemaWalletClient.ProviderMessage result =
                wemaWalletClient.validateNinOtp(
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

        if (wallet.getProviderStatus() == WemaWalletStatus.OTP_REQUIRED) {
            return response(
                    wallet,
                    "Wema is waiting for OTP validation before account generation can begin."
            );
        }

        requireProviderConfiguration();

        WemaWalletClient.PartnershipAccountDetails details =
                wemaWalletClient.getPartnershipAccountDetails(user.getPhone());

        if (details.accountNumber() == null || details.accountNumber().isBlank()) {
            wallet.setProviderStatus(WemaWalletStatus.PENDING);
            wallet.setProviderMessage(details.message());
        } else {
            String accountNumber = details.accountNumber().trim();
            if (!accountNumber.matches("\\d{10}")) {
                throw new IllegalStateException(
                        "Wema returned an invalid 10-digit wallet account number"
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
     * account has been created. The onboarding state and the bank's live account
     * status are deliberately kept separate.
     */
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = requireWallet(userId);

        String onboardingStatus = wallet.getProviderStatus().name();
        String accountStatus = null;
        String providerMessage = wallet.getProviderMessage();
        boolean balanceFresh = false;

        // Before Wema assigns a NUBAN, the local zero projection is harmless and
        // simply represents that no bank wallet is available yet. Once a NUBAN
        // exists, never display an old local projection as if it were fresh Wema
        // money when the provider refresh fails.
        BigDecimal visibleBalance = wallet.getProviderAccountNumber() == null
                ? wallet.getBalance()
                : BigDecimal.ZERO;

        String accountNumber = wallet.getProviderAccountNumber();
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                WemaWalletClient.WalletAccountDetails details =
                        wemaWalletClient.getWalletDetails(accountNumber);
                visibleBalance = details.availableBalance();
                balanceFresh = true;
                accountStatus = blankToNull(details.walletStatus());
            } catch (Exception e) {
                providerMessage =
                        "Wema balance is temporarily unavailable. Refresh before making a payment.";
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
                .providerStatus(onboardingStatus)
                .onboardingStatus(onboardingStatus)
                .accountStatus(accountStatus)
                .providerMessage(providerMessage)
                .onboardingRequired(
                        wallet.getProviderStatus() != WemaWalletStatus.ACTIVE
                )
                .balanceFresh(balanceFresh)
                .build();
    }

    @Transactional(readOnly = true)
    public WemaDiagnosticsResponse diagnostics(UUID userId) {
        Wallet wallet = requireWallet(userId);
        WemaWalletClient.Diagnostics provider = wemaWalletClient.diagnostics();

        return new WemaDiagnosticsResponse(
                provider.gatewayHost(),
                provider.gatewayLooksValid(),
                provider.apiKeyConfigured(),
                provider.subscriptionKeyConfigured(),
                wallet.getProviderStatus().name(),
                wallet.getProviderAccountNumber() != null &&
                        !wallet.getProviderAccountNumber().isBlank(),
                wallet.getProviderLastSyncedAt()
        );
    }

    private void requireProviderConfiguration() {
        WemaWalletClient.Diagnostics diagnostics = wemaWalletClient.diagnostics();

        if (!diagnostics.gatewayLooksValid()) {
            throw new WemaProviderException(
                    "WEMA_BASE_URL_INVALID",
                    "Wema Wallet Services gateway is not configured correctly on the Wave server.",
                    null
            );
        }

        if (!diagnostics.apiKeyConfigured()) {
            throw new WemaProviderException(
                    "WEMA_API_KEY_MISSING",
                    "Wema Wallet Services x-api-key is missing from the Wave server. Re-add WAVE_WEMA_WALLET_API_KEY in Render and redeploy.",
                    null
            );
        }

        if (!diagnostics.subscriptionKeyConfigured()) {
            throw new WemaProviderException(
                    "WEMA_SUBSCRIPTION_KEY_MISSING",
                    "Wema Wallet Services subscription key is missing from the Wave server. Re-add WAVE_WEMA_WALLET_SUBSCRIPTION_KEY in Render and redeploy.",
                    null
            );
        }
    }

    private User requireUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );
    }

    private Wallet requireWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Wallet not found")
                );
    }

    private WemaWalletActionResponse response(
            Wallet wallet,
            String message
    ) {
        return new WemaWalletActionResponse(
                wallet.getProviderStatus().name(),
                message,
                wallet.getProviderAccountNumber()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
