package com.wavetransakt.merchant.provider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider-neutral operational boundary for Wave Business acquiring.
 *
 * This contract models operations Wave will eventually need from an approved
 * acquiring provider. Implementations must remain fail-closed until the
 * provider has supplied the correct product credentials, merchant/terminal
 * identifiers, webhook verification rules and settlement configuration.
 */
public interface MerchantAcquiringGateway {

    String code();

    MerchantProvisioningResult provisionMerchant(MerchantProvisioningCommand command);

    TerminalLinkResult linkTerminal(TerminalLinkCommand command);

    TransactionInquiryResult queryTransaction(TransactionInquiryCommand command);

    SettlementInquiryResult querySettlement(SettlementInquiryCommand command);

    ProviderEventVerificationResult verifyProviderEvent(ProviderEventVerificationCommand command);

    record MerchantProvisioningCommand(
            String waveMerchantCode,
            String businessName,
            String businessType,
            String contactPhone,
            String contactEmail
    ) {
    }

    record MerchantProvisioningResult(
            String provider,
            String providerMerchantId,
            String status,
            String message
    ) {
    }

    record TerminalLinkCommand(
            String waveMerchantCode,
            String waveTerminalCode,
            String terminalSerialNumber
    ) {
    }

    record TerminalLinkResult(
            String provider,
            String providerTerminalId,
            String status,
            boolean cardAcceptanceEnabled,
            boolean contactlessEnabled,
            String message
    ) {
    }

    record TransactionInquiryCommand(
            String providerReference,
            String wavePaymentReference
    ) {
    }

    record TransactionInquiryResult(
            String provider,
            String providerReference,
            String wavePaymentReference,
            String status,
            BigDecimal amount,
            String currency,
            LocalDateTime providerUpdatedAt,
            String message
    ) {
    }

    record SettlementInquiryCommand(
            String providerSettlementReference,
            String providerMerchantId
    ) {
    }

    record SettlementInquiryResult(
            String provider,
            String providerSettlementReference,
            String status,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            String currency,
            LocalDateTime providerSettledAt,
            String message
    ) {
    }

    record ProviderEventVerificationCommand(
            String eventId,
            String eventType,
            String signature,
            String timestamp,
            byte[] rawPayload
    ) {
    }

    record ProviderEventVerificationResult(
            String provider,
            boolean verified,
            String eventId,
            String eventType,
            String status,
            String message
    ) {
    }
}
