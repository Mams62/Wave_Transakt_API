package com.wavetransakt.merchant.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Controlled-stage Interswitch merchant acquiring capability descriptor.
 *
 * Configuration presence is intentionally separated from provider-granted
 * capabilities. A client ID, merchant code or base URL must never be treated
 * as proof that Wave Transakt has been approved for merchant onboarding, QR,
 * Smart POS, contactless acceptance, settlement or reporting.
 */
@Component
public class InterswitchMerchantAcquiringProvider implements MerchantAcquiringProvider {

    private final boolean enabled;
    private final boolean merchantProductConfigured;
    private final boolean merchantOnboardingEnabled;
    private final boolean qrAcceptanceEnabled;
    private final boolean smartPosEnabled;
    private final boolean contactlessEnabled;
    private final boolean settlementEnabled;
    private final boolean reportingEnabled;

    public InterswitchMerchantAcquiringProvider(
            @Value("${interswitch.acquiring.enabled:false}") boolean enabled,
            @Value("${interswitch.acquiring.merchant-wallet-base-url:}") String merchantWalletBaseUrl,
            @Value("${interswitch.acquiring.merchant-code:}") String merchantCode,
            @Value("${interswitch.acquiring.merchant-onboarding-enabled:false}") boolean merchantOnboardingEnabled,
            @Value("${interswitch.acquiring.qr-acceptance-enabled:false}") boolean qrAcceptanceEnabled,
            @Value("${interswitch.acquiring.smart-pos-enabled:false}") boolean smartPosEnabled,
            @Value("${interswitch.acquiring.contactless-enabled:false}") boolean contactlessEnabled,
            @Value("${interswitch.acquiring.settlement-enabled:false}") boolean settlementEnabled,
            @Value("${interswitch.acquiring.reporting-enabled:false}") boolean reportingEnabled
    ) {
        this.enabled = enabled;
        this.merchantProductConfigured = hasText(merchantWalletBaseUrl) && hasText(merchantCode);
        this.merchantOnboardingEnabled = merchantOnboardingEnabled;
        this.qrAcceptanceEnabled = qrAcceptanceEnabled;
        this.smartPosEnabled = smartPosEnabled;
        this.contactlessEnabled = contactlessEnabled;
        this.settlementEnabled = settlementEnabled;
        this.reportingEnabled = reportingEnabled;
    }

    @Override
    public String code() {
        return "INTERSWITCH";
    }

    @Override
    public Capabilities capabilities() {
        boolean configured = enabled && merchantProductConfigured;

        boolean merchantOnboarding = configured && merchantOnboardingEnabled;
        boolean qrAcceptance = configured && qrAcceptanceEnabled;
        boolean cardAcceptance = configured && smartPosEnabled;
        boolean contactless = cardAcceptance && contactlessEnabled;
        boolean settlement = configured && settlementEnabled;
        boolean reporting = configured && reportingEnabled;

        String status;
        if (!enabled) {
            status = "PROVIDER_ONBOARDING_REQUIRED";
        } else if (!merchantProductConfigured) {
            status = "MERCHANT_PRODUCT_CONFIGURATION_REQUIRED";
        } else if (!(merchantOnboarding || qrAcceptance || cardAcceptance || settlement || reporting)) {
            status = "CONFIGURED_CAPABILITIES_NOT_GRANTED";
        } else {
            status = "CONFIGURED_WITH_EXPLICIT_CAPABILITIES";
        }

        return new Capabilities(
                configured,
                merchantOnboarding,
                qrAcceptance,
                cardAcceptance,
                contactless,
                settlement,
                reporting,
                status
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
