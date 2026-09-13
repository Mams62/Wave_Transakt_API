package com.wavetransakt.wallet.provider;

import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.service.WemaWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary adapter for the existing Wema implementation.
 *
 * Wema remains available for existing controlled-test data, but the rest of
 * Wave no longer needs to depend on WemaWalletService directly. This adapter
 * can coexist with an Interswitch implementation during migration.
 */
@Component
@RequiredArgsConstructor
public class WemaWalletProvider implements WalletProvider {

    private final WemaWalletService wemaWalletService;

    @Override
    public String code() {
        return "WEMA";
    }

    @Override
    public WalletResponse getWallet(UUID userId) {
        return wemaWalletService.getWallet(userId);
    }
}
