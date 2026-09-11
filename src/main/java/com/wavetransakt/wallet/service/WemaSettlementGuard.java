package com.wavetransakt.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Prevents the legacy local wallet projection from moving money while the
 * user-facing source of funds is Wema. This is intentionally closed by default
 * for controlled staging until Wema debit/settlement is wired end to end.
 */
@Component
public class WemaSettlementGuard {

    @Value("${wave.wallet.local-money-movement-enabled:false}")
    private boolean localMoneyMovementEnabled;

    public void requireMoneyMovementEnabled() {
        if (!localMoneyMovementEnabled) {
            throw new IllegalArgumentException(
                    "Money movement is temporarily paused while Wave Transakt completes Wema wallet settlement integration. Wallet setup, balance refresh, QR creation and recipient resolution remain available."
            );
        }
    }
}
