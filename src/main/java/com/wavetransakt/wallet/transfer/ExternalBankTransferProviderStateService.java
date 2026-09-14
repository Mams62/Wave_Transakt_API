package com.wavetransakt.wallet.transfer;

import com.wavetransakt.wallet.transfer.BankTransferGateway.TransferResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalBankTransferProviderStateService {

    private final ExternalBankTransferRepository transferRepository;

    @Transactional
    public ExternalBankTransfer markSubmitted(
            UUID userId,
            UUID transferId,
            String providerTransferCode,
            String providerMessage
    ) {
        ExternalBankTransfer transfer = lockedOwned(userId, transferId);
        if (transfer.getStatus() == ExternalBankTransferStatus.REVERSED
                || transfer.getStatus() == ExternalBankTransferStatus.FAILED
                || transfer.getStatus() == ExternalBankTransferStatus.SUCCESSFUL) {
            return transfer;
        }
        transfer.setProviderTransferCode(trim(providerTransferCode, 80));
        transfer.setProviderMessage(trim(providerMessage, 255));
        transfer.setStatus(ExternalBankTransferStatus.PENDING);
        transfer.setUpdatedAt(LocalDateTime.now());
        return transferRepository.save(transfer);
    }

    @Transactional
    public ExternalBankTransfer recordPendingOutcome(
            UUID userId,
            UUID transferId,
            TransferResult result,
            String overrideMessage
    ) {
        ExternalBankTransfer transfer = lockedOwned(userId, transferId);
        if (transfer.getStatus() == ExternalBankTransferStatus.REVERSED
                || transfer.getStatus() == ExternalBankTransferStatus.FAILED
                || transfer.getStatus() == ExternalBankTransferStatus.SUCCESSFUL) {
            return transfer;
        }

        transfer.setProviderReference(trim(result.providerReference(), 120));
        transfer.setProviderResponseCode(trim(result.providerCode(), 40));
        transfer.setProviderMessage(trim(
                overrideMessage == null ? result.message() : overrideMessage,
                255
        ));
        transfer.setStatus(ExternalBankTransferStatus.PENDING);
        transfer.setUpdatedAt(LocalDateTime.now());
        return transferRepository.save(transfer);
    }

    private ExternalBankTransfer lockedOwned(UUID userId, UUID transferId) {
        ExternalBankTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Bank transfer not found"));
        if (transfer.getWallet() == null
                || transfer.getWallet().getUser() == null
                || !transfer.getWallet().getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Bank transfer not found");
        }
        return transfer;
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
