package com.wavetransakt.wallet.provider;

import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.interswitch.InterswitchProviderException;
import com.wavetransakt.wallet.interswitch.InterswitchWalletClient;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Interswitch implementation of the provider-neutral Wave wallet boundary.
 *
 * Phase 1 is intentionally read-only: it can surface a provider balance when
 * sandbox access is configured, but it does not create wallets, transfer money,
 * fund accounts, or mark regulatory onboarding as complete.
 */
@Component
@RequiredArgsConstructor
public class InterswitchWalletProvider implements WalletProvider {

    private final WalletRepository walletRepository;
    private final InterswitchWalletClient interswitchWalletClient;

    @Override
    public String code() {
        return "INTERSWITCH";
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        BigDecimal visibleBalance = wallet.getBalance() == null
                ? BigDecimal.ZERO
                : wallet.getBalance();
        boolean balanceFresh = false;
        String providerStatus = "CONFIGURATION_REQUIRED";
        String providerMessage =
                "Interswitch sandbox access is not configured. Financial services remain locked.";

        if (interswitchWalletClient.isConfigured()) {
            providerStatus = "SANDBOX_READY";
            providerMessage =
                    "Interswitch sandbox is configured. Provider onboarding and settlement approval are still required before live money movement.";

            String walletIdentifier = resolveWalletIdentifier(wallet);
            if (walletIdentifier != null) {
                try {
                    InterswitchWalletClient.BalanceResult result =
                            interswitchWalletClient.getBalance(walletIdentifier);
                    visibleBalance = result.balance();
                    balanceFresh = true;
                    providerStatus = "READ_ONLY_CONNECTED";
                    // Do not echo raw provider messages into the customer API.
                    providerMessage =
                            "Interswitch wallet balance was refreshed successfully.";
                } catch (InterswitchProviderException ignored) {
                    // Fail closed. Do not expose raw provider payloads, credentials,
                    // wallet identifiers, or personally identifying values.
                    providerStatus = "PROVIDER_UNAVAILABLE";
                    providerMessage =
                            "Interswitch wallet data is temporarily unavailable. Financial services remain locked.";
                }
            } else {
                providerStatus = "ONBOARDING_REQUIRED";
                providerMessage =
                        "An approved Interswitch wallet identifier is required before provider balance can be loaded.";
            }
        }

        return WalletResponse.builder()
                .id(wallet.getId())
                .walletNumber(wallet.getWalletNumber())
                .balance(visibleBalance)
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .provider("INTERSWITCH")
                .bankName(null)
                .accountNumber(wallet.getProviderAccountNumber())
                .providerStatus(providerStatus)
                .onboardingStatus(providerStatus)
                .accountStatus(null)
                .providerMessage(providerMessage)
                .onboardingRequired(true)
                .balanceFresh(balanceFresh)
                .build();
    }

    /**
     * The first sandbox integration supports PHONE identifiers only when the
     * configured provider contract says PHONE. Other identifier types are not
     * guessed from NIN/BVN or other sensitive profile data.
     */
    private String resolveWalletIdentifier(Wallet wallet) {
        String configuredType = interswitchWalletClient.walletIdType();
        if (!"PHONE".equals(configuredType.toUpperCase(Locale.ROOT))) {
            return null;
        }
        if (wallet.getUser() == null ||
                wallet.getUser().getPhone() == null ||
                wallet.getUser().getPhone().isBlank()) {
            return null;
        }
        return wallet.getUser().getPhone().trim();
    }
}
