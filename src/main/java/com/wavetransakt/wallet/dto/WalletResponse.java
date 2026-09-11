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

    /** Wave Transakt internal wallet status (ACTIVE/SUSPENDED/etc). */
    private String status;

    /** External wallet provider for this controlled build. */
    private String provider;

    private String bankName;

    /** Wema NUBAN used to receive bank transfers. */
    private String accountNumber;

    /**
     * Compatibility field retained for older Android builds. It mirrors
     * onboardingStatus and must never be overwritten with Wema's live account
     * status strings.
     */
    private String providerStatus;

    /** Wave-side onboarding state: NOT_STARTED/OTP_REQUIRED/PENDING/ACTIVE/FAILED. */
    private String onboardingStatus;

    /** Wema account status once a NUBAN exists, e.g. Active/Dormant/PND. */
    private String accountStatus;

    private String providerMessage;

    /** True when the user still needs to complete Wema wallet onboarding. */
    private boolean onboardingRequired;

    /** True only when balance was refreshed successfully from the provider. */
    private boolean balanceFresh;
}
