package com.wavetransakt.wallet.transfer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Provider-neutral boundary for external Nigerian bank transfers.
 *
 * Internal Wave-to-Wave wallet transfers must not use this gateway.
 */
public interface BankTransferGateway {

    String code();

    Readiness readiness();

    List<Bank> banks();

    NameEnquiry resolveAccount(String bankCode, String accountNumber);

    TransferResult transfer(TransferCommand command);

    TransferResult query(String requestReference);

    record Readiness(
            String provider,
            boolean enabled,
            boolean productApproved,
            boolean readConfigured,
            boolean writeConfigured,
            boolean transferEnabled,
            String status
    ) {}

    record Bank(String bankCode, String bankName) {}

    record NameEnquiry(
            String provider,
            String bankCode,
            String accountNumber,
            String accountName,
            String providerCode,
            String message,
            boolean valid
    ) {}

    record TransferCommand(
            String requestReference,
            String transferCode,
            BigDecimal amount,
            String destinationBankCode,
            String destinationAccountNumber,
            String beneficiaryLastName,
            String beneficiaryOtherNames,
            String senderLastName,
            String senderOtherNames,
            String senderEmail,
            String senderPhone
    ) {}

    enum Outcome {
        SUCCESS,
        PENDING,
        FAILED,
        RETRYABLE
    }

    record TransferResult(
            String provider,
            Outcome outcome,
            String requestReference,
            String providerReference,
            String providerCode,
            String providerGrouping,
            String message
    ) {}
}
