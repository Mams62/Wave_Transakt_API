package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.dto.PosDtos;
import com.wavetransakt.merchant.entity.*;
import com.wavetransakt.merchant.repository.MerchantQrAddressRepository;
import com.wavetransakt.merchant.repository.MerchantSettlementBatchRepository;
import com.wavetransakt.merchant.repository.PosPaymentLookupRepository;
import com.wavetransakt.merchant.repository.PosTerminalLookupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PosClientService {

    private static final String MERCHANT_QR_PREFIX = "WTW:MERCHANT:";

    private final PosTerminalLookupRepository terminalLookupRepository;
    private final PosPaymentLookupRepository paymentLookupRepository;
    private final MerchantQrAddressRepository merchantQrAddressRepository;
    private final MerchantSettlementBatchRepository settlementBatchRepository;
    private final MerchantReceiptPolicy receiptPolicy;

    @Transactional(readOnly = true)
    public PosDtos.PosProfileResponse getProfile(UUID ownerUserId, String terminalCode) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        Merchant merchant = terminal.getMerchant();
        boolean providerLinked = hasText(terminal.getProviderCode()) && hasText(terminal.getProviderTerminalId());
        boolean anyAcceptanceCapability = terminal.isSupportsQr() || terminal.isSupportsCard();
        boolean paymentAcceptanceEnabled = terminal.getStatus() == PosTerminalStatus.ACTIVE
                && merchant.getStatus() == MerchantStatus.ACTIVE
                && providerLinked
                && anyAcceptanceCapability;

        return new PosDtos.PosProfileResponse(
                terminal.getId(),
                terminal.getTerminalCode(),
                terminal.getStatus().name(),
                merchant.getMerchantCode(),
                merchant.getBusinessName(),
                merchant.getBusinessType(),
                merchant.getStatus().name(),
                providerLinked,
                terminal.isSupportsQr(),
                terminal.isSupportsCard(),
                terminal.isSupportsNfc(),
                paymentAcceptanceEnabled,
                terminal.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PosDtos.PosQrResponse getQr(UUID ownerUserId, String terminalCode) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        Merchant merchant = terminal.getMerchant();
        MerchantQrAddress qr = merchantQrAddressRepository.findByMerchantId(merchant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant QR address not found"));

        boolean providerLinked = hasText(merchant.getProviderCode()) && hasText(merchant.getProviderMerchantId());
        boolean payable = qr.isActive()
                && terminal.isSupportsQr()
                && terminal.getStatus() == PosTerminalStatus.ACTIVE
                && merchant.getStatus() == MerchantStatus.ACTIVE
                && providerLinked;

        return new PosDtos.PosQrResponse(
                terminal.getTerminalCode(),
                merchant.getId(),
                merchant.getMerchantCode(),
                merchant.getBusinessName(),
                qr.getPublicId(),
                MERCHANT_QR_PREFIX + qr.getPublicId(),
                qr.isActive(),
                payable
        );
    }

    @Transactional(readOnly = true)
    public List<PosDtos.PosTransactionResponse> getTransactions(UUID ownerUserId, String terminalCode) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        return paymentLookupRepository.findAllByTerminalIdOrderByCreatedAtDesc(terminal.getId())
                .stream()
                .map(this::toTransaction)
                .toList();
    }

    @Transactional(readOnly = true)
    public PosDtos.PosReceiptResponse getReceipt(UUID ownerUserId, String terminalCode, UUID paymentId) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        MerchantPayment payment = paymentLookupRepository.findByIdAndTerminalId(paymentId, terminal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for terminal"));

        receiptPolicy.requireFinalReceiptAllowed(payment.getStatus());
        if (!hasText(payment.getReceiptNumber())) {
            throw new IllegalStateException("Provider-confirmed payment has no final receipt number yet");
        }

        Merchant merchant = terminal.getMerchant();
        return new PosDtos.PosReceiptResponse(
                payment.getReceiptNumber(),
                payment.getPaymentReference(),
                merchant.getMerchantCode(),
                merchant.getBusinessName(),
                terminal.getTerminalCode(),
                payment.getChannel().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getSettlementStatus().name(),
                payment.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<PosDtos.PosSettlementResponse> getSettlements(UUID ownerUserId, String terminalCode) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        return settlementBatchRepository.findAllByMerchantIdOrderByCreatedAtDesc(terminal.getMerchant().getId())
                .stream()
                .map(batch -> new PosDtos.PosSettlementResponse(
                        batch.getId(),
                        batch.getProviderCode(),
                        batch.getProviderBatchReference(),
                        batch.getCurrency(),
                        batch.getGrossAmount(),
                        batch.getFeeAmount(),
                        batch.getNetAmount(),
                        batch.getPaymentCount(),
                        batch.getStatus(),
                        batch.getSettlementDate(),
                        batch.getCreatedAt()
                ))
                .toList();
    }

    private PosTerminal requireOwnedTerminal(UUID ownerUserId, String terminalCode) {
        if (ownerUserId == null || terminalCode == null || terminalCode.isBlank()) {
            throw new IllegalArgumentException("Owner and terminal code are required");
        }

        PosTerminal terminal = terminalLookupRepository.findByTerminalCode(terminalCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("POS terminal not found"));

        if (terminal.getMerchant() == null
                || terminal.getMerchant().getOwner() == null
                || !ownerUserId.equals(terminal.getMerchant().getOwner().getId())) {
            throw new IllegalArgumentException("POS terminal not found");
        }
        return terminal;
    }

    private PosDtos.PosTransactionResponse toTransaction(MerchantPayment payment) {
        return new PosDtos.PosTransactionResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getChannel().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getReceiptNumber(),
                payment.getSettlementStatus().name(),
                payment.getProviderCode(),
                payment.getCreatedAt()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
