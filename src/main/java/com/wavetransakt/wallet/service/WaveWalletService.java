package com.wavetransakt.wallet.service;

import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.provider.WalletProvider;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing Wave wallet orchestration service.
 *
 * This is the boundary used by the public Wave API. It resolves the configured
 * provider for a wallet and delegates provider-specific work through
 * WalletProvider. The mobile apps and POS therefore remain independent of
 * Wema, Interswitch, or any future infrastructure partner.
 */
@Service
public class WaveWalletService {

    private final WalletRepository walletRepository;
    private final Map<String, WalletProvider> providers;

    public WaveWalletService(
            WalletRepository walletRepository,
            List<WalletProvider> providerImplementations
    ) {
        this.walletRepository = walletRepository;
        this.providers = new LinkedHashMap<>();

        for (WalletProvider provider : providerImplementations) {
            String code = normalizeProvider(provider.code());
            if (code == null) {
                throw new IllegalStateException("Wallet provider code must not be blank");
            }
            if (providers.putIfAbsent(code, provider) != null) {
                throw new IllegalStateException("Duplicate wallet provider: " + code);
            }
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        String providerCode = normalizeProvider(wallet.getProvider());
        if (providerCode == null) {
            throw new IllegalStateException("Wallet provider is not configured");
        }

        WalletProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new IllegalStateException(
                    "Wallet provider is not available: " + providerCode
            );
        }

        return provider.getWallet(userId);
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
