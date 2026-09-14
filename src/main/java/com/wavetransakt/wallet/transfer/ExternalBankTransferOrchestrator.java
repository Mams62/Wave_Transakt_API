package com.wavetransakt.wallet.transfer;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.transfer.BankTransferGateway.NameEnquiry;
import com.wavetransakt.wallet.transfer.BankTransferGateway.Outcome;
import com.wavetransakt.wallet.transfer.BankTransferGateway.Readiness;
import com.wavetransakt.wallet.transfer.BankTransferGateway.TransferCommand;
import com.wavetransakt.wallet.transfer.BankTransferGateway.TransferResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalBankTransferOrchestrator {

    private final InterswitchBankTransferGateway gateway;
    private final ExternalBankTransferReservationService reservationService;
    private final ExternalBankTransferProviderStateService providerStateService;
    private final UserRepository userRepository;

    @Value("${wave.transfers.external.execution-enabled:false}")
    private boolean executionEnabled;

    public ExecutionReadiness readiness() {
        Readiness provider = gateway.readiness();
        boolean ready = executionEnabled && provider.transferEnabled();
        String status;
        if (!executionEnabled) {
            status = "WAVE_EXECUTION_LOCKED";
        } else if (!provider.transferEnabled()) {
            status = provider.status();
        } else {
            status = "READY";
        }
        return new ExecutionReadiness(
                provider.provider(),
                executionEnabled,
                provider.enabled(),
                provider.productApproved(),
                provider.readConfigured(),
                provider.writeConfigured(),
                ready,
                status
        );
    }

    public ExternalBankTransfer initiate(
            UUID userId,
            String idempotencyKey,
            InitiateRequest request
    ) {
        requireExecutionReady();
        if (request == null) {
            throw new IllegalArgumentException("Transfer request is required");
        }

        NameEnquiry beneficiary = gateway.resolveAccount(
                request.bankCode(),
                request.accountNumber()
        );
        if (!beneficiary.valid()) {
            throw new IllegalArgumentException(
                    beneficiary.message() == null || beneficiary.message().isBlank()
                            ? "Beneficiary account could not be verified"
                            : beneficiary.message()
            );
        }

        ExternalBankTransferReservationService.Reservation reservation =
                reservationService.reserve(
                        userId,
                        idempotencyKey,
                        new ExternalBankTransferReservationService.CanonicalTransferRequest(
                                beneficiary.bankCode(),
                                beneficiary.accountNumber(),
                                beneficiary.accountName(),
                                request.amount(),
                                request.narration(),
                                request.transactionPin()
                        )
                );

        ExternalBankTransfer transfer = reservation.transfer();
        if (!reservation.created()
                && transfer.getStatus() != ExternalBankTransferStatus.RESERVED) {
            return transfer;
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found"));
        NameParts beneficiaryName = splitVerifiedName(beneficiary.accountName());
        String transferCode = gateway.deterministicTransferCode(transfer.getReference());

        providerStateService.markSubmitted(
                userId,
                transfer.getId(),
                transferCode,
                "Submitted to external transfer provider"
        );

        TransferResult result = gateway.transfer(new TransferCommand(
                transfer.getReference(),
                transferCode,
                transfer.getAmount(),
                transfer.getDestinationBankCode(),
                transfer.getDestinationAccountNumber(),
                beneficiaryName.lastName(),
                beneficiaryName.otherNames(),
                sender.getLastName(),
                sender.getFirstName(),
                sender.getEmail(),
                sender.getPhone()
        ));

        return applyProviderResult(userId, transfer, result);
    }

    public ExternalBankTransfer requery(UUID userId, String reference) {
        ExternalBankTransfer transfer = reservationService.findOwned(userId, reference);
        if (transfer.getStatus() == ExternalBankTransferStatus.REVERSED
                || transfer.getStatus() == ExternalBankTransferStatus.FAILED
                || transfer.getStatus() == ExternalBankTransferStatus.SUCCESSFUL) {
            return transfer;
        }

        Readiness readiness = gateway.readiness();
        if (!readiness.readConfigured()) {
            throw new IllegalStateException(
                    "External transfer provider inquiry is not configured"
            );
        }

        TransferResult result = gateway.query(transfer.getReference());
        return applyProviderResult(userId, transfer, result);
    }

    public ExternalBankTransfer findOwned(UUID userId, String reference) {
        return reservationService.findOwned(userId, reference);
    }

    private ExternalBankTransfer applyProviderResult(
            UUID userId,
            ExternalBankTransfer transfer,
            TransferResult result
    ) {
        if (result.outcome() == Outcome.FAILED) {
            return reservationService.reverseDefinitiveFailure(
                    userId,
                    transfer.getId(),
                    result.providerCode(),
                    result.message()
            );
        }

        String message = result.message();
        if (result.outcome() == Outcome.SUCCESS) {
            message = "Provider reported success; awaiting Wave settlement reconciliation before final completion";
        } else if (result.outcome() == Outcome.RETRYABLE) {
            message = "Beneficiary bank is temporarily unavailable; transfer remains reserved pending safe requery";
        } else if (message == null || message.isBlank()) {
            message = "Transfer outcome is pending provider confirmation";
        }

        return providerStateService.recordPendingOutcome(
                userId,
                transfer.getId(),
                result,
                message
        );
    }

    private void requireExecutionReady() {
        ExecutionReadiness readiness = readiness();
        if (!readiness.ready()) {
            throw new IllegalStateException(
                    "External bank transfer execution is locked until Interswitch product approval and Wave funding configuration are complete"
            );
        }
    }

    private NameParts splitVerifiedName(String accountName) {
        String normalized = accountName == null ? "" : accountName.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Verified beneficiary account name is missing");
        }
        int split = normalized.lastIndexOf(' ');
        if (split < 0) {
            return new NameParts(normalized, normalized);
        }
        String otherNames = normalized.substring(0, split).trim();
        String lastName = normalized.substring(split + 1).trim();
        if (otherNames.isBlank()) otherNames = normalized;
        if (lastName.isBlank()) lastName = normalized;
        return new NameParts(lastName, otherNames);
    }

    public record InitiateRequest(
            String bankCode,
            String accountNumber,
            BigDecimal amount,
            String narration,
            String transactionPin
    ) {}

    public record ExecutionReadiness(
            String provider,
            boolean executionEnabled,
            boolean providerEnabled,
            boolean productApproved,
            boolean readConfigured,
            boolean writeConfigured,
            boolean ready,
            String status
    ) {}

    private record NameParts(String lastName, String otherNames) {}
}
