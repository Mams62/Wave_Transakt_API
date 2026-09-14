package com.wavetransakt.wallet.transfer;

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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExternalBankTransferReservationService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^\\d{10}$");
    private static final DateTimeFormatter REFERENCE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ExternalBankTransferRepository transferRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExternalBankTransferLedgerService ledgerService;

    @Transactional
    public Reservation reserve(
            UUID userId,
            String rawIdempotencyKey,
            CanonicalTransferRequest request
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Transfer request is required");
        }

        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String bankCode = requireText(request.bankCode(), "Bank code", 20);
        String accountNumber = requireAccountNumber(request.accountNumber());
        String accountName = requireText(request.accountName(), "Verified account name", 160);
        BigDecimal amount = normalizeAmount(request.amount());
        String narration = optionalText(request.narration(), 160);

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

        String fingerprint = fingerprint(bankCode, accountNumber, accountName, amount, narration);
        ExternalBankTransfer existing = transferRepository
                .findByWalletIdAndIdempotencyKey(walletCandidate.getId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            assertSameRequest(existing, fingerprint);
            return new Reservation(existing, false);
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletCandidate.getId())
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        existing = transferRepository
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
            throw new IllegalArgumentException("Only NGN bank transfers are supported");
        }
        if (wallet.getBalance() == null || wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }

        LocalDateTime now = LocalDateTime.now();
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        ExternalBankTransfer transfer = ExternalBankTransfer.builder()
                .reference(generateReference())
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .wallet(wallet)
                .provider("INTERSWITCH")
                .destinationBankCode(bankCode)
                .destinationAccountNumber(accountNumber)
                .destinationAccountName(accountName)
                .amount(amount)
                .fee(BigDecimal.ZERO.setScale(2))
                .currency("NGN")
                .narration(narration)
                .status(ExternalBankTransferStatus.RESERVED)
                .providerMessage("Reserved for external transfer processing")
                .createdAt(now)
                .updatedAt(now)
                .build();

        transfer = transferRepository.save(transfer);
        ledgerService.recordReservation(
                transfer.getReference(),
                wallet,
                amount,
                "External bank transfer reservation"
        );
        return new Reservation(transfer, true);
    }

    @Transactional
    public ExternalBankTransfer reverseDefinitiveFailure(
            UUID userId,
            UUID transferId,
            String providerCode,
            String providerMessage
    ) {
        ExternalBankTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Bank transfer not found"));
        assertOwner(transfer, userId);

        if (transfer.getStatus() == ExternalBankTransferStatus.REVERSED
                || transfer.getStatus() == ExternalBankTransferStatus.FAILED) {
            return transfer;
        }
        if (transfer.getStatus() == ExternalBankTransferStatus.SUCCESSFUL) {
            throw new IllegalStateException("A successful bank transfer cannot be reversed as a provider failure");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(transfer.getWallet().getId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found during transfer reversal"));
        wallet.setBalance(wallet.getBalance().add(transfer.getAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        ledgerService.recordReversal(
                transfer.getReference(),
                wallet,
                transfer.getAmount(),
                "External bank transfer provider failure reversal"
        );

        transfer.setProviderResponseCode(optionalText(providerCode, 40));
        transfer.setProviderMessage(optionalText(providerMessage, 255));
        transfer.setStatus(ExternalBankTransferStatus.REVERSED);
        transfer.setUpdatedAt(LocalDateTime.now());
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public ExternalBankTransfer findOwned(UUID userId, String reference) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        return transferRepository.findByWalletIdAndReference(wallet.getId(), reference.trim())
                .orElseThrow(() -> new IllegalArgumentException("Bank transfer not found"));
    }

    private void assertOwner(ExternalBankTransfer transfer, UUID userId) {
        if (transfer.getWallet() == null
                || transfer.getWallet().getUser() == null
                || !transfer.getWallet().getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Bank transfer not found");
        }
    }

    private void assertSameRequest(ExternalBankTransfer existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key has already been used for a different bank transfer"
            );
        }
    }

    private String fingerprint(
            String bankCode,
            String accountNumber,
            String accountName,
            BigDecimal amount,
            String narration
    ) {
        String value = String.join("|",
                bankCode,
                accountNumber,
                accountName,
                amount.toPlainString(),
                narration == null ? "" : narration
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
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

    private String requireAccountNumber(String value) {
        String accountNumber = value == null ? "" : value.trim();
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("Account number must be exactly 10 digits");
        }
        return accountNumber;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String requireText(String value, String label, int max) {
        String normalized = optionalText(value, max);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private String optionalText(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String generateReference() {
        return "BT-" + LocalDateTime.now().format(REFERENCE_TIME) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public record Reservation(ExternalBankTransfer transfer, boolean created) {}

    public record CanonicalTransferRequest(
            String bankCode,
            String accountNumber,
            String accountName,
            BigDecimal amount,
            String narration,
            String transactionPin
    ) {}
}
