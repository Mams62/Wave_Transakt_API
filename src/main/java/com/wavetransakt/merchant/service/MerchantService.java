package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.dto.MerchantDtos;
import com.wavetransakt.merchant.entity.*;
import com.wavetransakt.merchant.repository.MerchantPaymentRepository;
import com.wavetransakt.merchant.repository.MerchantQrAddressRepository;
import com.wavetransakt.merchant.repository.MerchantReconciliationItemRepository;
import com.wavetransakt.merchant.repository.MerchantRepository;
import com.wavetransakt.merchant.repository.MerchantSettlementBatchRepository;
import com.wavetransakt.merchant.repository.PosTerminalRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private static final String MERCHANT_QR_PREFIX = "WTW:MERCHANT:";

    private final MerchantRepository merchantRepository;
    private final MerchantQrAddressRepository merchantQrAddressRepository;
    private final PosTerminalRepository posTerminalRepository;
    private final MerchantPaymentRepository merchantPaymentRepository;
    private final MerchantSettlementBatchRepository merchantSettlementBatchRepository;
    private final MerchantReconciliationItemRepository merchantReconciliationItemRepository;
    private final UserRepository userRepository;

    /**
     * Creates a Wave Business merchant profile only.
     *
     * This does not represent provider approval, acquiring activation, wallet
     * provisioning, settlement setup, or permission to accept card payments.
     */
    @Transactional
    public MerchantDtos.MerchantResponse createMerchant(
            UUID ownerUserId,
            MerchantDtos.CreateMerchantRequest request
    ) {
        User owner = requireUser(ownerUserId);

        Merchant merchant = Merchant.builder()
                .owner(owner)
                .merchantCode(newMerchantCode())
                .businessName(normalizeRequired(request.businessName(), "Business name"))
                .businessType(normalizeRequired(request.businessType(), "Business type").toUpperCase(Locale.ROOT))
                .status(MerchantStatus.DRAFT)
                .providerCode(null)
                .providerMerchantId(null)
                .build();

        merchant = merchantRepository.save(merchant);

        MerchantQrAddress qrAddress = MerchantQrAddress.builder()
                .merchant(merchant)
                .publicId(newMerchantQrPublicId())
                .active(true)
                .build();
        merchantQrAddressRepository.save(qrAddress);

        return toMerchantResponse(merchant);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.MerchantResponse> getOwnedMerchants(UUID ownerUserId) {
        requireUser(ownerUserId);
        return merchantRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerUserId)
                .stream()
                .map(this::toMerchantResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.MerchantQrResponse getMerchantQr(UUID ownerUserId, UUID merchantId) {
        Merchant merchant = requireOwnedMerchant(ownerUserId, merchantId);
        MerchantQrAddress qrAddress = merchantQrAddressRepository.findByMerchantId(merchant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant QR address not found"));

        return new MerchantDtos.MerchantQrResponse(
                merchant.getId(),
                qrAddress.getPublicId(),
                MERCHANT_QR_PREFIX + qrAddress.getPublicId(),
                qrAddress.isActive()
        );
    }

    /**
     * Resolves identity only. It never creates or authorizes a payment.
     */
    @Transactional(readOnly = true)
    public MerchantDtos.MerchantQrResolveResponse resolveMerchantQr(String payloadOrPublicId) {
        String publicId = extractPublicId(payloadOrPublicId);
        MerchantQrAddress qrAddress = merchantQrAddressRepository.findByPublicIdAndActiveTrue(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant QR is invalid or inactive"));

        Merchant merchant = qrAddress.getMerchant();
        boolean providerLinked = hasText(merchant.getProviderCode()) && hasText(merchant.getProviderMerchantId());
        boolean payable = merchant.getStatus() == MerchantStatus.ACTIVE && providerLinked;

        return new MerchantDtos.MerchantQrResolveResponse(
                merchant.getId(),
                merchant.getMerchantCode(),
                merchant.getBusinessName(),
                merchant.getBusinessType(),
                merchant.getStatus().name(),
                payable
        );
    }

    /**
     * Registers a Wave-side terminal placeholder. NFC remains disabled until a
     * real acquiring provider has linked and activated the physical terminal.
     */
    @Transactional
    public MerchantDtos.PosTerminalResponse registerTerminal(UUID ownerUserId, UUID merchantId) {
        Merchant merchant = requireOwnedMerchant(ownerUserId, merchantId);

        PosTerminal terminal = PosTerminal.builder()
                .merchant(merchant)
                .terminalCode(newTerminalCode())
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .supportsQr(true)
                .supportsNfc(false)
                .providerCode(null)
                .providerTerminalId(null)
                .build();

        return toTerminalResponse(posTerminalRepository.save(terminal));
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.PosTerminalResponse> getTerminals(UUID ownerUserId, UUID merchantId) {
        Merchant merchant = requireOwnedMerchant(ownerUserId, merchantId);
        return posTerminalRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchant.getId())
                .stream()
                .map(this::toTerminalResponse)
                .toList();
    }

    /**
     * Read-only payment history. Provider callbacks/reconciliation will be the
     * only components allowed to advance provider-backed payment states later.
     */
    @Transactional(readOnly = true)
    public List<MerchantDtos.MerchantPaymentResponse> getPayments(UUID ownerUserId, UUID merchantId) {
        Merchant merchant = requireOwnedMerchant(ownerUserId, merchantId);
        return merchantPaymentRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchant.getId())
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    /**
     * Read-only provider settlement history. No endpoint in this service can
     * manufacture, submit, accelerate or mark a settlement complete.
     */
    @Transactional(readOnly = true)
    public List<MerchantDtos.SettlementBatchResponse> getSettlementBatches(
            UUID ownerUserId,
            UUID merchantId
    ) {
        Merchant merchant = requireOwnedMerchant(ownerUserId, merchantId);
        return merchantSettlementBatchRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchant.getId())
                .stream()
                .map(this::toSettlementBatchResponse)
                .toList();
    }

    /**
     * Read-only reconciliation details for an owned merchant settlement batch.
     */
    @Transactional(readOnly = true)
    public List<MerchantDtos.ReconciliationItemResponse> getReconciliationItems(
            UUID ownerUserId,
            UUID merchantId,
            UUID settlementBatchId
    ) {
        Merchant merchant = requireOwnedMerchant(ownerUserId, merchantId);
        MerchantSettlementBatch batch = merchantSettlementBatchRepository
                .findByIdAndMerchantId(settlementBatchId, merchant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Settlement batch not found"));

        return merchantReconciliationItemRepository
                .findAllBySettlementBatchIdOrderByCreatedAtAsc(batch.getId())
                .stream()
                .map(this::toReconciliationItemResponse)
                .toList();
    }

    private User requireUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Merchant requireOwnedMerchant(UUID ownerUserId, UUID merchantId) {
        if (ownerUserId == null || merchantId == null) {
            throw new IllegalArgumentException("Merchant and owner are required");
        }
        return merchantRepository.findByIdAndOwnerId(merchantId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
    }

    private MerchantDtos.MerchantResponse toMerchantResponse(Merchant merchant) {
        boolean providerLinked = hasText(merchant.getProviderCode()) && hasText(merchant.getProviderMerchantId());
        return new MerchantDtos.MerchantResponse(
                merchant.getId(),
                merchant.getMerchantCode(),
                merchant.getBusinessName(),
                merchant.getBusinessType(),
                merchant.getStatus().name(),
                merchant.getProviderCode(),
                providerLinked,
                merchant.getCreatedAt()
        );
    }

    private MerchantDtos.PosTerminalResponse toTerminalResponse(PosTerminal terminal) {
        boolean providerLinked = hasText(terminal.getProviderCode()) && hasText(terminal.getProviderTerminalId());
        return new MerchantDtos.PosTerminalResponse(
                terminal.getId(),
                terminal.getTerminalCode(),
                terminal.getStatus().name(),
                terminal.getProviderCode(),
                providerLinked,
                terminal.isSupportsQr(),
                terminal.isSupportsNfc(),
                terminal.getCreatedAt()
        );
    }

    private MerchantDtos.MerchantPaymentResponse toPaymentResponse(MerchantPayment payment) {
        return new MerchantDtos.MerchantPaymentResponse(
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

    private MerchantDtos.SettlementBatchResponse toSettlementBatchResponse(MerchantSettlementBatch batch) {
        return new MerchantDtos.SettlementBatchResponse(
                batch.getId(),
                batch.getProviderCode(),
                batch.getCurrency(),
                batch.getGrossAmount(),
                batch.getFeeAmount(),
                batch.getNetAmount(),
                batch.getPaymentCount(),
                batch.getStatus(),
                batch.getSettlementDate(),
                batch.getCreatedAt()
        );
    }

    private MerchantDtos.ReconciliationItemResponse toReconciliationItemResponse(MerchantReconciliationItem item) {
        MerchantPayment payment = item.getMerchantPayment();
        return new MerchantDtos.ReconciliationItemResponse(
                item.getId(),
                payment.getId(),
                payment.getPaymentReference(),
                item.getExpectedAmount(),
                item.getProviderAmount(),
                item.getDifferenceAmount(),
                item.getStatus(),
                item.getReason(),
                item.getCreatedAt()
        );
    }

    private String extractPublicId(String value) {
        String normalized = normalizeRequired(value, "Merchant QR payload");
        if (normalized.startsWith(MERCHANT_QR_PREFIX)) {
            normalized = normalized.substring(MERCHANT_QR_PREFIX.length());
        }
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new IllegalArgumentException("Merchant QR is invalid");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String newMerchantCode() {
        return "WTM-" + compactUuid(12);
    }

    private String newMerchantQrPublicId() {
        return "MQ-" + compactUuid(24);
    }

    private String newTerminalCode() {
        return "WTPOS-" + compactUuid(16);
    }

    private String compactUuid(int length) {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return value.substring(0, Math.min(length, value.length()));
    }
}
