package com.wavetransakt.merchant.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe, non-secret readiness view for the Interswitch merchant/POS integration.
 *
 * This class deliberately reports only configuration/grant booleans and blocker
 * labels. It must never expose credentials, access tokens, merchant identifiers,
 * terminal identifiers, signing material, settlement accounts or provider payloads.
 */
@Component
public class InterswitchProvisioningReadiness {

    private final InterswitchMerchantAcquiringProvider provider;
    private final boolean terminalLinkEnabled;
    private final boolean transactionInquiryEnabled;
    private final boolean settlementInquiryEnabled;
    private final boolean webhookVerificationConfigured;
    private final boolean cardIssuingEnabled;

    public InterswitchProvisioningReadiness(
            InterswitchMerchantAcquiringProvider provider,
            @Value("${interswitch.acquiring.terminal-link-enabled:false}") boolean terminalLinkEnabled,
            @Value("${interswitch.acquiring.transaction-inquiry-enabled:false}") boolean transactionInquiryEnabled,
            @Value("${interswitch.acquiring.settlement-inquiry-enabled:false}") boolean settlementInquiryEnabled,
            @Value("${interswitch.acquiring.webhook-verification-configured:false}") boolean webhookVerificationConfigured,
            @Value("${interswitch.acquiring.card-issuing-enabled:false}") boolean cardIssuingEnabled
    ) {
        this.provider = provider;
        this.terminalLinkEnabled = terminalLinkEnabled;
        this.transactionInquiryEnabled = transactionInquiryEnabled;
        this.settlementInquiryEnabled = settlementInquiryEnabled;
        this.webhookVerificationConfigured = webhookVerificationConfigured;
        this.cardIssuingEnabled = cardIssuingEnabled;
    }

    public Readiness snapshot() {
        MerchantAcquiringProvider.Capabilities capabilities = provider.capabilities();

        boolean merchantOnboardingReady = capabilities.configured()
                && capabilities.merchantOnboardingAvailable();
        boolean terminalProvisioningReady = merchantOnboardingReady
                && capabilities.cardAcceptanceAvailable()
                && terminalLinkEnabled;
        boolean contactlessReady = terminalProvisioningReady
                && capabilities.contactlessAvailable();
        boolean reconciliationReady = capabilities.configured()
                && capabilities.settlementAvailable()
                && capabilities.reportingAvailable()
                && transactionInquiryEnabled
                && settlementInquiryEnabled
                && webhookVerificationConfigured;
        boolean cardIssuingReady = capabilities.configured() && cardIssuingEnabled;

        List<String> blockers = new ArrayList<>();
        if (!capabilities.configured()) {
            blockers.add("INTERSWITCH_MERCHANT_PRODUCT_CONFIGURATION_REQUIRED");
        }
        if (!capabilities.merchantOnboardingAvailable()) {
            blockers.add("MERCHANT_ONBOARDING_GRANT_REQUIRED");
        }
        if (!capabilities.cardAcceptanceAvailable()) {
            blockers.add("SMARTPOS_CARD_ACCEPTANCE_GRANT_REQUIRED");
        }
        if (!terminalLinkEnabled) {
            blockers.add("SMARTPOS_TERMINAL_LINK_CONTRACT_REQUIRED");
        }
        if (!capabilities.contactlessAvailable()) {
            blockers.add("CONTACTLESS_ACCEPTANCE_GRANT_REQUIRED");
        }
        if (!transactionInquiryEnabled) {
            blockers.add("TRANSACTION_INQUIRY_PRODUCT_REQUIRED");
        }
        if (!capabilities.settlementAvailable() || !settlementInquiryEnabled) {
            blockers.add("SETTLEMENT_INQUIRY_PRODUCT_REQUIRED");
        }
        if (!capabilities.reportingAvailable()) {
            blockers.add("REPORTING_PRODUCT_REQUIRED");
        }
        if (!webhookVerificationConfigured) {
            blockers.add("WEBHOOK_VERIFICATION_CONTRACT_REQUIRED");
        }
        if (!cardIssuingEnabled) {
            blockers.add("CARD_ISSUING_PROGRAM_REQUIRED");
        }

        return new Readiness(
                provider.code(),
                capabilities.configured(),
                merchantOnboardingReady,
                terminalProvisioningReady,
                contactlessReady,
                reconciliationReady,
                cardIssuingReady,
                List.copyOf(blockers)
        );
    }

    public record Readiness(
            String provider,
            boolean providerConfigured,
            boolean merchantOnboardingReady,
            boolean terminalProvisioningReady,
            boolean contactlessReady,
            boolean reconciliationReady,
            boolean cardIssuingReady,
            List<String> blockers
    ) {
    }
}
