package com.wavetransakt.qr.service;

import com.wavetransakt.qr.dto.PaymentQrResponse;
import com.wavetransakt.qr.entity.PaymentQrIntent;
import com.wavetransakt.qr.entity.PaymentQrStatus;
import com.wavetransakt.qr.repository.PaymentQrIntentRepository;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.service.TransactionService;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentQrService {

    private static final String PREFIX =
            "WTW:PAY:1:";

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9._:-]{8,128}$"
            );

    private final PaymentQrIntentRepository
            paymentQrIntentRepository;

    private final WalletRepository walletRepository;

    private final TransactionService transactionService;

    /**
     * Creates an expiring, one-payment QR.
     */
    @Transactional
    public PaymentQrResponse create(
            UUID recipientUserId,
            BigDecimal rawAmount,
            String rawDescription
    ) {

        Wallet wallet =
                walletRepository
                        .findByUserId(recipientUserId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        validateWallet(wallet);

        BigDecimal amount =
                normalizeAmount(rawAmount);

        String description =
                normalizeDescription(
                        rawDescription
                );

        UUID intentId =
                UUID.randomUUID();

        String nonce =
                generateNonce();

        LocalDateTime now =
                LocalDateTime.now();

        PaymentQrIntent intent =
                PaymentQrIntent.builder()
                        .id(intentId)
                        .recipientWallet(wallet)
                        .nonce(nonce)
                        .amount(amount)
                        .currency(wallet.getCurrency())
                        .description(description)
                        .status(PaymentQrStatus.ACTIVE)
                        .expiresAt(
                                now.plusMinutes(10)
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        paymentQrIntentRepository.save(intent);

        return toResponse(intent);
    }

    /**
     * Resolve an intent without moving money.
     */
    @Transactional
    public PaymentQrResponse resolve(
            String payload
    ) {

        ParsedPayload parsed =
                parsePayload(payload);

        PaymentQrIntent intent =
                paymentQrIntentRepository
                        .findByIdForUpdate(
                                parsed.intentId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment QR not found"
                                )
                        );

        validateNonce(
                intent,
                parsed.nonce()
        );

        expireIfNecessary(intent);

        validateWallet(
                intent.getRecipientWallet()
        );

        return toResponse(intent);
    }

    /**
     * Consume a dynamic QR and perform exactly one
     * financial transaction.
     */
    @Transactional
    public TransactionResponse pay(
            UUID payerUserId,
            String rawIdempotencyKey,
            String payload
    ) {

        String idempotencyKey =
                normalizeIdempotencyKey(
                        rawIdempotencyKey
                );

        ParsedPayload parsed =
                parsePayload(payload);

        /*
         * Lock the intent BEFORE checking status.
         *
         * Only one concurrent payment can consume it.
         */
        PaymentQrIntent intent =
                paymentQrIntentRepository
                        .findByIdForUpdate(
                                parsed.intentId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment QR not found"
                                )
                        );

        validateNonce(
                intent,
                parsed.nonce()
        );

        expireIfNecessary(intent);

        /*
         * Same payment retry.
         *
         * If the original request succeeded but the client
         * lost the HTTP response, the same idempotency key
         * is allowed to retrieve the original transaction.
         */
        if (intent.getStatus() ==
                PaymentQrStatus.CONSUMED) {

            if (idempotencyKey.equals(
                    intent.getConsumedIdempotencyKey()
            )) {

                return executeTransfer(
                        payerUserId,
                        idempotencyKey,
                        intent
                );
            }

            throw new IllegalArgumentException(
                    "Payment QR has already been used"
            );
        }

        if (intent.getStatus() ==
                PaymentQrStatus.EXPIRED) {

            throw new IllegalArgumentException(
                    "Payment QR has expired"
            );
        }

        if (intent.getStatus() ==
                PaymentQrStatus.CANCELLED) {

            throw new IllegalArgumentException(
                    "Payment QR has been cancelled"
            );
        }

        if (intent.getStatus() !=
                PaymentQrStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Payment QR is unavailable"
            );
        }

        Wallet recipient =
                intent.getRecipientWallet();

        validateWallet(recipient);

        User recipientUser =
                recipient.getUser();

        if (recipientUser == null) {
            throw new IllegalArgumentException(
                    "Recipient is unavailable"
            );
        }

        if (recipientUser
                .getId()
                .equals(payerUserId)) {

            throw new IllegalArgumentException(
                    "You cannot pay your own payment QR"
            );
        }

        TransactionResponse transaction =
                executeTransfer(
                        payerUserId,
                        idempotencyKey,
                        intent
                );

        /*
         * We only consume the QR AFTER the transfer
         * successfully completes.
         *
         * Since everything is under the same Spring DB
         * transaction, failure rolls all of this back.
         */
        intent.setStatus(
                PaymentQrStatus.CONSUMED
        );

        intent.setConsumedAt(
                LocalDateTime.now()
        );

        intent.setConsumedIdempotencyKey(
                idempotencyKey
        );

        intent.setTransactionReference(
                transaction.getReference()
        );

        intent.setUpdatedAt(
                LocalDateTime.now()
        );

        paymentQrIntentRepository.save(intent);

        return transaction;
    }

    private TransactionResponse executeTransfer(
            UUID payerUserId,
            String idempotencyKey,
            PaymentQrIntent intent
    ) {

        String internalDescription =
                buildTransactionDescription(
                        intent
                );

        TransferRequest transferRequest =
                TransferRequest.builder()
                        .receiverWalletNumber(
                                intent
                                        .getRecipientWallet()
                                        .getWalletNumber()
                        )
                        .amount(
                                intent.getAmount()
                        )
                        .description(
                                internalDescription
                        )
                        .build();

        return transactionService.transfer(
                payerUserId,
                idempotencyKey,
                transferRequest
        );
    }

    /**
     * Including the payment intent ID in the transaction
     * description binds the Stage-4 request fingerprint to
     * this specific QR intent.
     */
    private String buildTransactionDescription(
            PaymentQrIntent intent
    ) {

        String base =
                "QR:" + intent.getId();

        String userDescription =
                intent.getDescription();

        if (userDescription == null ||
                userDescription.isBlank()) {

            return base;
        }

        String result =
                base + " | " + userDescription;

        if (result.length() > 255) {
            return result.substring(0, 255);
        }

        return result;
    }

    private void expireIfNecessary(
            PaymentQrIntent intent
    ) {

        if (intent.getStatus() ==
                PaymentQrStatus.ACTIVE &&
                LocalDateTime.now().isAfter(
                        intent.getExpiresAt()
                )) {

            intent.setStatus(
                    PaymentQrStatus.EXPIRED
            );

            intent.setUpdatedAt(
                    LocalDateTime.now()
            );

            paymentQrIntentRepository.save(intent);
        }
    }

    private PaymentQrResponse toResponse(
            PaymentQrIntent intent
    ) {

        Wallet wallet =
                intent.getRecipientWallet();

        User user =
                wallet.getUser();

        if (user == null) {
            throw new IllegalArgumentException(
                    "Wallet owner not found"
            );
        }

        String accountName =
                buildAccountName(user);

        return PaymentQrResponse.builder()
                .intentId(intent.getId())
                .qrType("PAYMENT")
                .payload(
                        buildPayload(intent)
                )
                .walletNumber(
                        wallet.getWalletNumber()
                )
                .accountName(accountName)
                .amount(intent.getAmount())
                .currency(intent.getCurrency())
                .description(
                        intent.getDescription()
                )
                .status(
                        intent.getStatus().name()
                )
                .expiresAt(
                        intent.getExpiresAt()
                )
                .build();
    }

    private String buildPayload(
            PaymentQrIntent intent
    ) {

        return PREFIX +
                intent.getId() +
                ":" +
                intent.getNonce();
    }

    private ParsedPayload parsePayload(
            String payload
    ) {

        if (payload == null ||
                payload.isBlank()) {

            throw new IllegalArgumentException(
                    "QR payload is required"
            );
        }

        String normalized =
                payload.trim();

        if (!normalized.startsWith(PREFIX)) {

            throw new IllegalArgumentException(
                    "Invalid Wave Transakt payment QR"
            );
        }

        String body =
                normalized.substring(
                        PREFIX.length()
                );

        String[] parts =
                body.split(":", 2);

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid payment QR payload"
            );
        }

        UUID intentId;

        try {
            intentId =
                    UUID.fromString(parts[0]);

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid payment QR identifier"
            );
        }

        String nonce =
                parts[1].trim();

        if (nonce.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid payment QR nonce"
            );
        }

        return new ParsedPayload(
                intentId,
                nonce
        );
    }

    private void validateNonce(
            PaymentQrIntent intent,
            String suppliedNonce
    ) {

        byte[] expected =
                intent.getNonce()
                        .getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        );

        byte[] supplied =
                suppliedNonce
                        .getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        );

        if (!MessageDigest.isEqual(
                expected,
                supplied
        )) {

            throw new IllegalArgumentException(
                    "Invalid payment QR"
            );
        }
    }

    private String generateNonce() {

        byte[] random =
                new byte[24];

        SECURE_RANDOM.nextBytes(random);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(random);
    }

    private BigDecimal normalizeAmount(
            BigDecimal amount
    ) {

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Amount is required"
            );
        }

        if (amount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        try {

            return amount.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );

        } catch (ArithmeticException e) {

            throw new IllegalArgumentException(
                    "Amount must not contain more than 2 decimal places"
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

        /*
         * Reserve space for the internal QR intent ID that
         * becomes part of the transaction fingerprint.
         */
        if (normalized.length() > 190) {
            throw new IllegalArgumentException(
                    "Description must not exceed 190 characters"
            );
        }

        return normalized;
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
                    "Invalid Idempotency-Key"
            );
        }

        return key;
    }

    private void validateWallet(
            Wallet wallet
    ) {

        if (wallet == null ||
                wallet.getStatus() !=
                        WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Recipient wallet is not active"
            );
        }
    }

    private String buildAccountName(
            User user
    ) {

        String first =
                user.getFirstName() == null
                        ? ""
                        : user.getFirstName().trim();

        String last =
                user.getLastName() == null
                        ? ""
                        : user.getLastName().trim();

        String name =
                (first + " " + last).trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Account name is unavailable"
            );
        }

        return name;
    }

    private record ParsedPayload(
            UUID intentId,
            String nonce
    ) {
    }
}