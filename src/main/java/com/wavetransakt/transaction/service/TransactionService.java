package com.wavetransakt.transaction.service;

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

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public TransactionResponse transfer(
            UUID senderUserId,
            String rawIdempotencyKey,
            TransferRequest request
    ) {

        if (senderUserId == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required"
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
         * Resolve wallet identities.
         *
         * These objects are only used to determine IDs.
         * Balance mutation happens only after pessimistic locks.
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

        if (senderCandidate
                .getId()
                .equals(receiverCandidate.getId())) {

            throw new IllegalArgumentException(
                    "You cannot transfer money to your own wallet"
            );
        }

        String requestFingerprint =
                generateRequestFingerprint(
                        receiverCandidate.getId(),
                        amount,
                        description
                );

        /*
         * Fast idempotency check.
         *
         * Most retries will be resolved here without
         * acquiring wallet locks.
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
         * Deterministic lock ordering protects against
         * transfer deadlocks such as:
         *
         * Wallet A -> Wallet B
         * Wallet B -> Wallet A
         */
        UUID firstLockId;
        UUID secondLockId;

        if (senderWalletId.compareTo(
                receiverWalletId
        ) < 0) {

            firstLockId =
                    senderWalletId;

            secondLockId =
                    receiverWalletId;

        } else {

            firstLockId =
                    receiverWalletId;

            secondLockId =
                    senderWalletId;
        }

        Wallet firstLockedWallet =
                walletRepository
                        .findByIdForUpdate(firstLockId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        Wallet secondLockedWallet =
                walletRepository
                        .findByIdForUpdate(secondLockId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        Wallet sender =
                firstLockedWallet
                        .getId()
                        .equals(senderWalletId)

                        ? firstLockedWallet
                        : secondLockedWallet;

        Wallet receiver =
                firstLockedWallet
                        .getId()
                        .equals(receiverWalletId)

                        ? firstLockedWallet
                        : secondLockedWallet;

        /*
         * CRITICAL:
         *
         * Recheck idempotency AFTER acquiring the sender
         * wallet lock.
         *
         * Two simultaneous requests may both pass the first
         * lookup before either transaction commits.
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
         * All financial checks occur while the wallet
         * rows are locked.
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

        if (sender.getBalance()
                .compareTo(amount) < 0) {

            throw new IllegalArgumentException(
                    "Insufficient wallet balance"
            );
        }

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
                        .currency(sender.getCurrency())
                        .type(
                                TransactionType.TRANSFER
                        )
                        .status(
                                TransactionStatus.SUCCESSFUL
                        )
                        .description(description)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        transaction =
                transactionRepository
                        .save(transaction);

        return toResponse(transaction);
    }

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
                    "This Idempotency-Key has already " +
                            "been used for a different transfer"
            );
        }

        /*
         * Same sender + same key + same request.
         *
         * Return the original financial result.
         * Never debit again.
         */
        return toResponse(existing);
    }

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
                    "Idempotency-Key must be 8 to 128 " +
                            "characters and contain only letters, " +
                            "numbers, '.', '_', ':', or '-'"
            );
        }

        return key;
    }

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

    private BigDecimal normalizeAmount(
            BigDecimal amount
    ) {

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Transfer amount is required"
            );
        }

        if (amount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Transfer amount must be greater than zero"
            );
        }

        try {

            /*
             * Financial amounts are stored at 2 decimal
             * places. We reject rather than silently round.
             */
            return amount.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );

        } catch (ArithmeticException ex) {

            throw new IllegalArgumentException(
                    "Transfer amount must not contain " +
                            "more than 2 decimal places"
            );
        }
    }

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
                    "Description must not exceed " +
                            "255 characters"
            );
        }

        return normalized;
    }

    private String generateRequestFingerprint(
            UUID receiverWalletId,
            BigDecimal amount,
            String description
    ) {

        /*
         * Canonical representation of the user's exact
         * financial instruction.
         *
         * We use the receiver's immutable UUID rather than
         * display text.
         */
        String canonicalRequest =
                "TRANSFER\n" +
                        receiverWalletId +
                        "\n" +
                        amount.toPlainString() +
                        "\n" +
                        (description == null
                                ? ""
                                : description);

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

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }

    private String generateReference() {

        String reference;

        do {

            String random =
                    Long.toString(
                            ThreadLocalRandom.current()
                                    .nextLong(
                                            100000,
                                            1000000
                                    )
                    );

            String date =
                    LocalDateTime.now()
                            .toLocalDate()
                            .toString()
                            .replace("-", "");

            reference =
                    "WT-" +
                            date +
                            "-" +
                            random;

        } while (
                transactionRepository
                        .existsByReference(reference)
        );

        return reference;
    }

    private TransactionResponse toResponse(
            Transaction transaction
    ) {

        return TransactionResponse.builder()
                .id(transaction.getId())
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
                        transaction.getType()
                )
                .status(
                        transaction.getStatus()
                )
                .description(
                        transaction.getDescription()
                )
                .createdAt(
                        transaction.getCreatedAt()
                )
                .build();
    }

    public Object transfer(UUID id, TransferRequest build) {
        return null;
    }
}