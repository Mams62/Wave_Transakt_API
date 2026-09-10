package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.entity.ServicePayment;
import com.wavetransakt.serviceprovider.entity.ServicePaymentStatus;
import com.wavetransakt.serviceprovider.repository.ServicePaymentRepository;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderOutcome;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderResult;
import com.wavetransakt.transaction.exception.IdempotencyConflictException;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ServicePaymentReservationService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private static final DateTimeFormatter PROVIDER_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final DateTimeFormatter REFERENCE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ServicePaymentRepository servicePaymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServicePaymentLedgerService servicePaymentLedgerService;

    @Transactional
    public Reservation reserve(
            UUID userId,
            String rawIdempotencyKey,
            CanonicalServiceRequest request
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found"));

        if (user.getTransactionPinHash() == null || user.getTransactionPinHash().isBlank()) {
            throw new IllegalArgumentException("Transaction PIN is not configured");
        }

        if (!passwordEncoder.matches(request.transactionPin(), user.getTransactionPinHash())) {
            throw new IllegalArgumentException("Invalid transaction PIN");
        }

        Wallet walletCandidate = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        String fingerprint = fingerprint(request);

        ServicePayment existing = servicePaymentRepository
                .findByWalletIdAndIdempotencyKey(walletCandidate.getId(), idempotencyKey)
                .orElse(null);

        if (existing != null) {
            assertSameRequest(existing, fingerprint);
            return new Reservation(existing, false);
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletCandidate.getId())
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        existing = servicePaymentRepository
                .findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey)
                .orElse(null);

        if (existing != null) {
            assertSameRequest(existing, fingerprint);
            return new Reservation(existing, false);
        }

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException("Wallet is not active");
        }
        if (!"NGN".equalsIgnoreCase(wallet.getCurrency())) {
            throw new IllegalArgumentException("Only NGN service payments are supported");
        }
        if (wallet.getBalance() == null || wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }

        LocalDateTime now = LocalDateTime.now();
        wallet.setBalance(wallet.getBalance().subtract(request.amount()));
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        ServicePayment payment = ServicePayment.builder()
                .reference(generateReference())
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .wallet(wallet)
                .provider("VTPASS")
                .serviceKind(request.serviceKind())
                .serviceId(request.serviceId())
                .serviceName(request.serviceName())
                .variationCode(request.variationCode())
                .recipient(request.recipient())
                .amount(request.amount())
                .currency("NGN")
                .providerRequestId(generateProviderRequestId())
                .status(ServicePaymentStatus.PENDING)
                .providerStatus("CREATED")
                .providerMessage("Reserved for provider processing")
                .createdAt(now)
                .updatedAt(now)
                .build();

        payment = servicePaymentRepository.save(payment);

        servicePaymentLedgerService.recordReservation(
                payment.getReference(),
                wallet,
                payment.getAmount(),
                payment.getServiceName() + " service payment reservation"
        );

        return new Reservation(payment, true);
    }

    @Transactional
    public ServicePayment applyProviderResult(
            UUID userId,
            UUID paymentId,
            ProviderResult result
    ) {
        ServicePayment payment = servicePaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Service payment not found"));
        assertOwner(payment, userId);

        if (payment.getStatus() != ServicePaymentStatus.PENDING) {
            return payment;
        }

        payment.setProviderStatus(trim(result.providerStatus(), 80));
        payment.setProviderTransactionId(trim(result.transactionId(), 120));
        payment.setProviderMessage(trim(result.message(), 255));

        if (result.outcome() == ProviderOutcome.SUCCESS) {
            payment.setStatus(ServicePaymentStatus.SUCCESSFUL);
        } else if (result.outcome() == ProviderOutcome.FAILED) {
            Wallet wallet = walletRepository.findByIdForUpdate(payment.getWallet().getId())
                    .orElseThrow(() -> new IllegalStateException("Wallet not found during reversal"));

            wallet.setBalance(wallet.getBalance().add(payment.getAmount()));
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);

            servicePaymentLedgerService.recordReversal(
                    payment.getReference(),
                    wallet,
                    payment.getAmount(),
                    payment.getServiceName() + " provider failure reversal"
            );
            payment.setStatus(ServicePaymentStatus.FAILED);
        }

        payment.setUpdatedAt(LocalDateTime.now());
        return servicePaymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public ServicePayment findOwned(UUID userId, String reference) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        return servicePaymentRepository
                .findByWalletIdAndReference(wallet.getId(), reference.trim())
                .orElseThrow(() -> new IllegalArgumentException("Service payment not found"));
    }

    private void assertOwner(ServicePayment payment, UUID userId) {
        if (payment.getWallet() == null ||
                payment.getWallet().getUser() == null ||
                !payment.getWallet().getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Service payment not found");
        }
    }

    private void assertSameRequest(ServicePayment existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key has already been used for a different service payment"
            );
        }
    }

    private String normalizeIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String key = rawKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
        return key;
    }

    private String fingerprint(CanonicalServiceRequest request) {
        String value = String.join("|",
                request.serviceKind(),
                request.serviceId(),
                request.variationCode() == null ? "" : request.variationCode(),
                request.recipient(),
                request.amount().toPlainString()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String generateProviderRequestId() {
        String prefix = ZonedDateTime.now(ZoneId.of("Africa/Lagos"))
                .format(PROVIDER_TIME);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return prefix + suffix;
    }

    private String generateReference() {
        return "SV-" + LocalDateTime.now().format(REFERENCE_TIME) + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    public record Reservation(ServicePayment payment, boolean created) {
    }

    public record CanonicalServiceRequest(
            String serviceKind,
            String serviceId,
            String serviceName,
            String variationCode,
            String recipient,
            BigDecimal amount,
            String transactionPin
    ) {
    }
}
