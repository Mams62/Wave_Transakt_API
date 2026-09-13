package com.wavetransakt.merchant.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-closed Interswitch operational adapter.
 *
 * No provider endpoint is called from this class yet. The public Interswitch
 * documentation confirms merchant-wallet and transaction-query products exist,
 * but Wave must first receive its assigned test merchant/product access,
 * SmartPOS terminal provisioning model and webhook verification contract.
 */
@Component
public class InterswitchMerchantAcquiringGateway implements MerchantAcquiringGateway {

    private final boolean enabled;
    private final boolean merchantOnboardingEnabled;
    private final boolean terminalLinkEnabled;
    private final boolean transactionInquiryEnabled;
    private final boolean settlementInquiryEnabled;
    private final boolean webhookVerificationConfigured;

    public InterswitchMerchantAcquiringGateway(
            @Value("${interswitch.acquiring.enabled:false}") boolean enabled,
            @Value("${interswitch.acquiring.merchant-onboarding-enabled:false}") boolean merchantOnboardingEnabled,
            @Value("${interswitch.acquiring.terminal-link-enabled:false}") boolean terminalLinkEnabled,
            @Value("${interswitch.acquiring.transaction-inquiry-enabled:false}") boolean transactionInquiryEnabled,
            @Value("${interswitch.acquiring.settlement-inquiry-enabled:false}") boolean settlementInquiryEnabled,
            @Value("${interswitch.acquiring.webhook-verification-configured:false}") boolean webhookVerificationConfigured
    ) {
        this.enabled = enabled;
        this.merchantOnboardingEnabled = merchantOnboardingEnabled;
        this.terminalLinkEnabled = terminalLinkEnabled;
        this.transactionInquiryEnabled = transactionInquiryEnabled;
        this.settlementInquiryEnabled = settlementInquiryEnabled;
        this.webhookVerificationConfigured = webhookVerificationConfigured;
    }

    @Override
    public String code() {
        return "INTERSWITCH";
    }

    @Override
    public MerchantProvisioningResult provisionMerchant(MerchantProvisioningCommand command) {
        requireEnabled(merchantOnboardingEnabled, "Interswitch merchant onboarding is not enabled for Wave Transakt");
        throw notImplemented("Interswitch merchant onboarding adapter is awaiting assigned provider credentials and product contract");
    }

    @Override
    public TerminalLinkResult linkTerminal(TerminalLinkCommand command) {
        requireEnabled(terminalLinkEnabled, "Interswitch terminal linking is not enabled for Wave Transakt");
        throw notImplemented("Interswitch SmartPOS terminal linking is awaiting the provider terminal provisioning contract");
    }

    @Override
    public TransactionInquiryResult queryTransaction(TransactionInquiryCommand command) {
        requireEnabled(transactionInquiryEnabled, "Interswitch transaction inquiry is not enabled for Wave Transakt");
        throw notImplemented("Interswitch transaction inquiry adapter is awaiting assigned Transaction Search/product configuration");
    }

    @Override
    public SettlementInquiryResult querySettlement(SettlementInquiryCommand command) {
        requireEnabled(settlementInquiryEnabled, "Interswitch settlement inquiry is not enabled for Wave Transakt");
        throw notImplemented("Interswitch settlement inquiry adapter is awaiting the contracted settlement/reporting API");
    }

    @Override
    public ProviderEventVerificationResult verifyProviderEvent(ProviderEventVerificationCommand command) {
        requireEnabled(webhookVerificationConfigured, "Interswitch webhook verification is not configured for Wave Transakt");
        throw notImplemented("Interswitch webhook verification is awaiting the provider-issued signature verification specification");
    }

    private void requireEnabled(boolean operationEnabled, String message) {
        if (!enabled || !operationEnabled) {
            throw new MerchantAcquiringOperationException(message);
        }
    }

    private MerchantAcquiringOperationException notImplemented(String message) {
        return new MerchantAcquiringOperationException(message);
    }
}
