package com.wavetransakt.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    private UUID id;

    /** Stable Wave Transakt internal wallet identifier used by QR/P2P. */
    private String walletNumber;

    /** Current user-facing balance. When Wema is active this comes from Wema. */
    private BigDecimal balance;

    private String currency;

    private String status;

    /** External wallet provider for this controlled build. */
    private String provider;

    private String bankName;

    /** Wema NUBAN used to receive bank transfers. */
    private String accountNumber;

    private String providerStatus;

    private String providerMessage;

    /** True when the user still needs to complete Wema wallet onboarding. */
    private boolean onboardingRequired;

    /** True only when balance was refreshed successfully from the provider. */
    private boolean balanceFresh;
}
