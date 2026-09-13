package com.wavetransakt.merchant.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Controlled-stage Interswitch merchant acquiring capability descriptor.
 *
 * Configuration flags mean only that Wave has been explicitly configured for
 * a provider product. They do not mean a merchant, terminal, card program or
 * settlement account has been approved by Interswitch.
 */
@Component
public class InterswitchMerchantAcquiringProvider implements MerchantAcquiringProvider {

    private final boolean enabled;
    private final boolean merchantWalletConfigured;
    private final boolean smartPosEnabled;
    private final boolean contactlessEnabled;

    public InterswitchMerchantAcquiringProvider(
            @Value("${interswitch.acquiring.enabled:false}") boolean enabled,
            @Value("${interswitch.acquiring.merchant-wallet-base-url:}") String merchantWalletBaseUrl,
            @Value("${interswitch.acquiring.merchant-code:}") String merchantCode,
            @Value("${interswitch.acquiring.smart-pos-enabled:false}") boolean smartPosEnabled,
            @Value("${interswitch.acquiring.contactless-enabled:false}") boolean contactlessEnabled
    ) {
        this.enabled = enabled;
        this.merchantWalletConfigured = hasText(merchantWalletBaseUrl) && hasText(merchantCode);
        this.smartPosEnabled = smartPosEnabled;
        this.contactlessEnabled = contactlessEnabled;
    }

    @Override
    public String code() {
        return "INTERSWITCH";
    }

    @Override
    public Capabilities capabilities() {
        boolean configured = enabled && merchantWalletConfigured;
        boolean cardAcceptance = configured && smartPosEnabled;
        boolean contactless = cardAcceptance && contactlessEnabled;

        String status;
        if (!enabled) {
            status = "PROVIDER_ONBOARDING_REQUIRED";
        } else if (!merchantWalletConfigured) {
            status = "MERCHANT_PRODUCT_CONFIGURATION_REQUIRED";
        } else if (!smartPosEnabled) {
            status = "MERCHANT_WALLET_CONFIGURED_POS_PENDING";
        } else {
            status = "CONFIGURED_PENDING_MERCHANT_AND_TERMINAL_APPROVAL";
        }

        return new Capabilities(
                configured,
                configured,
                configured,
                cardAcceptance,
                contactless,
                cardAcceptance,
                cardAcceptance,
                status
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
