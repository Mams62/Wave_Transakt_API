package com.wavetransakt.transaction.service;

import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.entity.Transaction;
import com.wavetransakt.transaction.entity.TransactionStatus;
import com.wavetransakt.transaction.entity.TransactionType;
import com.wavetransakt.transaction.exception.IdempotencyConflictException;
import com.wavetransakt.transaction.repository.TransactionRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9._:-]{8,128}$"
            );

    private static final BigDecimal MIN_TRANSFER_AMOUNT =
            new BigDecimal("1.00");

    private final TransactionRepository
            transactionRepository;

    private final WalletRepository
            walletRepository;

    /*
     * Stage 6:
     * Double-entry accounting ledger.
     */
    private final LedgerService
            ledgerService;

    /**
     * ============================================================
     * WALLET TO WALLET TRANSFER
     * ============================================================
     *
     * Protections:
     *
     * - authenticated sender supplied by controller
     * - Idempotency-Key
     * - request fingerprint
     * - deterministic pessimistic wallet locking
     * - balance check after locking
     * - atomic sender/receiver balance mutation
     * - double-entry ledger
     *
     * Everything runs inside one database transaction.
     */
    @Transactional
    public TransactionResponse transfer(
            UUID senderUserId,
            String rawIdempotencyKey,
            TransferRequest request
    ) {

        if (senderUserId == null) {
            throw new IllegalArgumentException(
                    "Sender user is required"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Transfer request is required"
            );
        }

        String idempotencyKey =
                normalizeIdempotencyKey(
                        rawIdempotencyKey
                );

        String receiverWalletNumber =
                normalizeWalletNumber(
                        request.getReceiverWalletNumber()
                );

        BigDecimal amount =
                normalizeAmount(
                        request.getAmount()
                );

        String description =
                normalizeDescription(
                        request.getDescription()
                );

        /*
         * ========================================================
         * RESOLVE WALLETS BEFORE LOCKING
         * ========================================================
         */

        Wallet senderCandidate =
                walletRepository
                        .findByUserId(senderUserId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Sender wallet not found"
                                )
                        );

        Wallet receiverCandidate =
                walletRepository
                        .findByWalletNumber(
                                receiverWalletNumber
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Receiver wallet not found"
                                )
                        );

        if (senderCandidate.getId().equals(
                receiverCandidate.getId()
        )) {

            throw new IllegalArgumentException(
                    "You cannot transfer money to your own wallet"
            );
        }

        /*
         * Fingerprint represents the exact financial instruction.
         *
         * Same:
         * sender + key + fingerprint
         *
         * means retry.
         *
         * Same key with another fingerprint means conflict.
         */
        String requestFingerprint =
                generateRequestFingerprint(
                        receiverCandidate.getId(),
                        amount,
                        description
                );

        /*
         * ========================================================
         * FAST IDEMPOTENCY CHECK
         * ========================================================
         */

        Transaction existing =
                transactionRepository
                        .findBySenderWalletIdAndIdempotencyKey(
                                senderCandidate.getId(),
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {

            return resolveIdempotentReplay(
                    existing,
                    requestFingerprint
            );
        }

        UUID senderWalletId =
                senderCandidate.getId();

        UUID receiverWalletId =
                receiverCandidate.getId();

        /*
         * ========================================================
         * DETERMINISTIC WALLET LOCK ORDER
         * ========================================================
         *
         * Lock UUIDs in the same global order to reduce
         * deadlock risk.
         */

        UUID firstWalletId;
        UUID secondWalletId;

        if (senderWalletId.compareTo(
                receiverWalletId
        ) < 0) {

            firstWalletId =
                    senderWalletId;

            secondWalletId =
                    receiverWalletId;

        } else {

            firstWalletId =
                    receiverWalletId;

            secondWalletId =
                    senderWalletId;
        }

        Wallet firstLockedWallet =
                walletRepository
                        .findByIdForUpdate(
                                firstWalletId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        Wallet secondLockedWallet =
                walletRepository
                        .findByIdForUpdate(
                                secondWalletId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        Wallet sender;
        Wallet receiver;

        if (firstLockedWallet
                .getId()
                .equals(senderWalletId)) {

            sender =
                    firstLockedWallet;

            receiver =
                    secondLockedWallet;

        } else {

            sender =
                    secondLockedWallet;

            receiver =
                    firstLockedWallet;
        }

        /*
         * ========================================================
         * IDEMPOTENCY RECHECK AFTER LOCK
         * ========================================================
         *
         * Critical concurrency protection.
         *
         * Another request may have completed while this request
         * was waiting for the wallet lock.
         */

        existing =
                transactionRepository
                        .findBySenderWalletIdAndIdempotencyKey(
                                senderWalletId,
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null) {

            return resolveIdempotentReplay(
                    existing,
                    requestFingerprint
            );
        }

        /*
         * ========================================================
         * FINANCIAL VALIDATION WHILE LOCKED
         * ========================================================
         */

        if (sender.getStatus() !=
                WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Sender wallet is not active"
            );
        }

        if (receiver.getStatus() !=
                WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Receiver wallet is not active"
            );
        }

        if (sender.getBalance() == null) {

            throw new IllegalStateException(
                    "Sender wallet balance is unavailable"
            );
        }

        if (receiver.getBalance() == null) {

            throw new IllegalStateException(
                    "Receiver wallet balance is unavailable"
            );
        }

        if (sender.getCurrency() == null ||
                receiver.getCurrency() == null) {

            throw new IllegalStateException(
                    "Wallet currency is unavailable"
            );
        }

        if (!sender.getCurrency().equals(
                receiver.getCurrency()
        )) {

            throw new IllegalArgumentException(
                    "Wallet currencies do not match"
            );
        }

        if (sender.getBalance()
                .compareTo(amount) < 0) {

            throw new IllegalArgumentException(
                    "Insufficient wallet balance"
            );
        }

        /*
         * ========================================================
         * BALANCE PROJECTION UPDATE
         * ========================================================
         */

        sender.setBalance(
                sender.getBalance()
                        .subtract(amount)
        );

        receiver.setBalance(
                receiver.getBalance()
                        .add(amount)
        );

        LocalDateTime now =
                LocalDateTime.now();

        sender.setUpdatedAt(now);
        receiver.setUpdatedAt(now);

        walletRepository.save(sender);
        walletRepository.save(receiver);

        /*
         * ========================================================
         * BUSINESS TRANSACTION
         * ========================================================
         */

        Transaction transaction =
                Transaction.builder()
                        .reference(
                                generateReference()
                        )
                        .idempotencyKey(
                                idempotencyKey
                        )
                        .requestFingerprint(
                                requestFingerprint
                        )
                        .senderWallet(sender)
                        .receiverWallet(receiver)
                        .amount(amount)
                        .currency(
                                sender.getCurrency()
                        )
                        .type(
                                TransactionType.TRANSFER
                        )
                        .status(
                                TransactionStatus.SUCCESSFUL
                        )
                        .description(
                                description
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        transaction =
                transactionRepository
                        .save(transaction);

        /*
         * ========================================================
         * STAGE 6 — DOUBLE ENTRY LEDGER
         * ========================================================
         *
         * Sender wallet is a liability account.
         *
         * Sending money reduces the liability:
         *
         *      DEBIT sender wallet
         *
         * Receiving money increases the liability:
         *
         *      CREDIT receiver wallet
         *
         * Example:
         *
         *      DEBIT  Sender     5,000
         *      CREDIT Receiver   5,000
         *
         * Total debit == total credit.
         *
         * This happens within the SAME Spring transaction as
         * the wallet mutation and transaction record.
         */

        ledgerService.recordWalletTransfer(
                transaction.getReference(),
                sender,
                receiver,
                amount,
                transaction.getCurrency(),
                description
        );

        return toResponse(
                transaction
        );
    }

    /**
     * ============================================================
     * IDEMPOTENCY REPLAY
     * ============================================================
     *
     * Same key + same request:
     *
     * return the original transaction.
     *
     * Same key + changed request:
     *
     * reject with HTTP 409 via GlobalExceptionHandler.
     */
    private TransactionResponse
    resolveIdempotentReplay(
            Transaction existing,
            String expectedFingerprint
    ) {

        String existingFingerprint =
                existing.getRequestFingerprint();

        if (existingFingerprint == null ||
                !existingFingerprint.equals(
                        expectedFingerprint
                )) {

            throw new IdempotencyConflictException(
                    "Idempotency-Key has already been used for a different transfer"
            );
        }

        return toResponse(
                existing
        );
    }

    /**
     * ============================================================
     * IDEMPOTENCY KEY VALIDATION
     * ============================================================
     */
    private String normalizeIdempotencyKey(
            String rawKey
    ) {

        if (rawKey == null ||
                rawKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key header is required"
            );
        }

        String key =
                rawKey.trim();

        if (!IDEMPOTENCY_KEY_PATTERN
                .matcher(key)
                .matches()) {

            throw new IllegalArgumentException(
                    "Invalid Idempotency-Key"
            );
        }

        return key;
    }

    /**
     * ============================================================
     * WALLET NUMBER NORMALIZATION
     * ============================================================
     */
    private String normalizeWalletNumber(
            String walletNumber
    ) {

        if (walletNumber == null ||
                walletNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Receiver wallet number is required"
            );
        }

        return walletNumber.trim();
    }

    /**
     * ============================================================
     * AMOUNT NORMALIZATION
     * ============================================================
     *
     * NGN transaction amount is stored with exactly two
     * decimal places.
     */
    private BigDecimal normalizeAmount(
            BigDecimal amount
    ) {

        if (amount == null) {

            throw new IllegalArgumentException(
                    "Amount is required"
            );
        }

        BigDecimal normalized;

        try {

            normalized =
                    amount.setScale(
                            2,
                            RoundingMode.UNNECESSARY
                    );

        } catch (ArithmeticException e) {

            throw new IllegalArgumentException(
                    "Amount must not contain more than 2 decimal places"
            );
        }

        if (normalized.compareTo(
                MIN_TRANSFER_AMOUNT
        ) < 0) {

            throw new IllegalArgumentException(
                    "Amount must be at least 1.00"
            );
        }

        return normalized;
    }

    /**
     * ============================================================
     * DESCRIPTION NORMALIZATION
     * ============================================================
     */
    private String normalizeDescription(
            String description
    ) {

        if (description == null) {
            return null;
        }

        String normalized =
                description.trim();

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() > 255) {

            throw new IllegalArgumentException(
                    "Description must not exceed 255 characters"
            );
        }

        return normalized;
    }

    /**
     * ============================================================
     * REQUEST FINGERPRINT
     * ============================================================
     *
     * SHA-256(
     *
     *     TRANSFER
     *     receiver UUID
     *     normalized amount
     *     normalized description
     *
     * )
     */
    private String generateRequestFingerprint(
            UUID receiverWalletId,
            BigDecimal amount,
            String description
    ) {

        String canonicalRequest =
                "TRANSFER\n" +
                        receiverWalletId +
                        "\n" +
                        amount.toPlainString() +
                        "\n" +
                        (
                                description == null
                                        ? ""
                                        : description
                        );

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            canonicalRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    e
            );
        }
    }

    /**
     * ============================================================
     * TRANSACTION REFERENCE
     * ============================================================
     */
    private String generateReference() {

        String reference;

        do {

            String date =
                    LocalDateTime.now()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "yyyyMMdd"
                                    )
                            );

            int random =
                    ThreadLocalRandom
                            .current()
                            .nextInt(
                                    100000,
                                    1000000
                            );

            reference =
                    "WT-" +
                            date +
                            "-" +
                            random;

        } while (
                transactionRepository
                        .existsByReference(
                                reference
                        )
        );

        return reference;
    }

    /**
     * ============================================================
     * API RESPONSE
     * ============================================================
     */
    private TransactionResponse toResponse(
            Transaction transaction
    ) {

        return TransactionResponse.builder()
                .id(
                        transaction.getId()
                )
                .reference(
                        transaction.getReference()
                )
                .senderWalletNumber(
                        transaction
                                .getSenderWallet()
                                .getWalletNumber()
                )
                .receiverWalletNumber(
                        transaction
                                .getReceiverWallet()
                                .getWalletNumber()
                )
                .amount(
                        transaction.getAmount()
                )
                .currency(
                        transaction.getCurrency()
                )
                .type(
                        TransactionType.valueOf(transaction
                                .getType()
                                .name())
                )
                .status(
                        TransactionStatus.valueOf(transaction
                                .getStatus()
                                .name())
                )
                .description(
                        transaction.getDescription()
                )
                .createdAt(
                        transaction.getCreatedAt()
                )
                .build();
    }
}