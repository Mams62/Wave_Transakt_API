package com.wavetransakt.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavetransakt.payment.dto.InitializePaymentRequest;
import com.wavetransakt.payment.dto.PaymentResponse;
import com.wavetransakt.payment.entity.PaymentTransaction;
import com.wavetransakt.payment.entity.PaymentTransactionStatus;
import com.wavetransakt.payment.repository.PaymentTransactionRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaystackService {

    private static final String BASE_URL =
            "https://api.paystack.co/transaction/";

    private static final BigDecimal MINIMUM_FUNDING_AMOUNT =
            new BigDecimal("100.00");

    private final RestTemplate restTemplate;

    private final PaymentTransactionRepository
            paymentTransactionRepository;

    private final UserRepository
            userRepository;

    private final WalletRepository
            walletRepository;

    private final ObjectMapper
            objectMapper;

    private final PaymentSettlementService
            paymentSettlementService;

    @Value("${paystack.secret-key}")
    private String secretKey;

    /**
     * ============================================================
     * INITIALIZE PAYSTACK WALLET FUNDING
     * ============================================================
     *
     * Android sends NAIRA.
     *
     * Example:
     *
     * Android -> 100.00
     * Backend -> 10000 kobo
     * Paystack -> 10000
     */
    public PaymentResponse initializeTransaction(
            InitializePaymentRequest request,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        if (request == null ||
                request.getAmount() == null) {

            throw new IllegalArgumentException(
                    "Payment amount is required"
            );
        }

        BigDecimal amount =
                normalizeNairaAmount(
                        request.getAmount()
                );

        if (amount.compareTo(
                MINIMUM_FUNDING_AMOUNT
        ) < 0) {

            throw new IllegalArgumentException(
                    "Minimum wallet funding amount is ₦100"
            );
        }

        Wallet wallet =
                walletRepository
                        .findByUserId(
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        if (wallet.getStatus() !=
                WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Wallet is not available for funding"
            );
        }

        if (user.getEmail() == null ||
                user.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "Account email is unavailable"
            );
        }

        /*
         * Convert naira to kobo exactly.
         *
         * ₦100.00 -> 10000
         */
        long amountKobo;

        try {

            amountKobo =
                    amount
                            .movePointRight(2)
                            .longValueExact();

        } catch (ArithmeticException e) {

            throw new IllegalArgumentException(
                    "Invalid payment amount",
                    e
            );
        }

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(
                secretKey
        );

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        String body;

        try {

            body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "email",
                                    user.getEmail(),
                                    "amount",
                                    amountKobo,
                                    "currency",
                                    "NGN"
                            )
                    );

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Unable to create Paystack request",
                    e
            );
        }

        HttpEntity<String> entity =
                new HttpEntity<>(
                        body,
                        headers
                );

        ResponseEntity<String> response =
                restTemplate.exchange(
                        BASE_URL + "initialize",
                        HttpMethod.POST,
                        entity,
                        String.class
                );

        if (!response
                .getStatusCode()
                .is2xxSuccessful() ||
                response.getBody() == null) {

            throw new IllegalArgumentException(
                    "Unable to initialize Paystack transaction"
            );
        }

        JsonNode root =
                parseJson(
                        response.getBody()
                );

        boolean providerStatus =
                root.path("status")
                        .asBoolean(false);

        if (!providerStatus) {

            throw new IllegalArgumentException(
                    root.path("message")
                            .asText(
                                    "Paystack initialization failed"
                            )
            );
        }

        JsonNode data =
                root.path("data");

        String reference =
                data.path("reference")
                        .asText(null);

        String accessCode =
                data.path("access_code")
                        .asText(null);

        String authorizationUrl =
                data.path("authorization_url")
                        .asText(null);

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack did not return a transaction reference"
            );
        }

        if (accessCode == null ||
                accessCode.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack did not return an access code"
            );
        }

        if (paymentTransactionRepository
                .existsByReference(
                        reference
                )) {

            throw new IllegalArgumentException(
                    "Duplicate Paystack payment reference"
            );
        }

        /*
         * Store Wave Transakt transaction amount in NAIRA.
         */
        PaymentTransaction transaction =
                PaymentTransaction.builder()
                        .user(user)
                        .wallet(wallet)
                        .reference(reference)
                        .amount(amount)
                        .currency("NGN")
                        .provider("PAYSTACK")
                        .status(
                                PaymentTransactionStatus.PENDING
                        )
                        .build();

        paymentTransactionRepository.save(
                transaction
        );

        /*
         * Return a clean Wave Transakt API contract.
         *
         * Do not expose Paystack's nested response directly
         * to Android.
         */
        return PaymentResponse.builder()
                .reference(reference)
                .amount(amount)
                .status("PENDING")
                .authorizationUrl(
                        authorizationUrl
                )
                .accessCode(
                        accessCode
                )
                .walletBalance(
                        wallet.getBalance()
                )
                .build();
    }

    /**
     * ============================================================
     * VERIFY PAYSTACK TRANSACTION
     * ============================================================
     */
    public PaymentResponse verifyTransaction(
            String reference,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment reference is required"
            );
        }

        reference =
                reference.trim();

        PaymentTransaction localTransaction =
                paymentTransactionRepository
                        .findByReference(
                                reference
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment transaction not found"
                                )
                        );

        if (!localTransaction
                .getUser()
                .getId()
                .equals(
                        user.getId()
                )) {

            throw new IllegalArgumentException(
                    "You are not authorized to verify this transaction"
            );
        }

        /*
         * Fast idempotent replay.
         */
        if (localTransaction.getStatus() ==
                PaymentTransactionStatus.SUCCESSFUL) {

            Wallet wallet =
                    walletRepository
                            .findByUserId(
                                    user.getId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Wallet not found"
                                    )
                            );

            return PaymentResponse.builder()
                    .reference(reference)
                    .amount(
                            localTransaction.getAmount()
                    )
                    .status("SUCCESSFUL")
                    .walletBalance(
                            wallet.getBalance()
                    )
                    .build();
        }

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(
                secretKey
        );

        HttpEntity<Void> entity =
                new HttpEntity<>(
                        headers
                );

        ResponseEntity<String> response =
                restTemplate.exchange(
                        BASE_URL +
                                "verify/" +
                                reference,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

        if (!response
                .getStatusCode()
                .is2xxSuccessful() ||
                response.getBody() == null) {

            throw new IllegalArgumentException(
                    "Unable to verify Paystack transaction"
            );
        }

        JsonNode root =
                parseJson(
                        response.getBody()
                );

        boolean apiStatus =
                root.path("status")
                        .asBoolean(false);

        if (!apiStatus) {

            return PaymentResponse.builder()
                    .reference(reference)
                    .amount(
                            localTransaction.getAmount()
                    )
                    .status("PENDING")
                    .build();
        }

        JsonNode data =
                root.path("data");

        String providerReference =
                data.path("reference")
                        .asText("");

        if (!reference.equals(
                providerReference
        )) {

            throw new IllegalArgumentException(
                    "Paystack reference mismatch"
            );
        }

        String paystackStatus =
                data.path("status")
                        .asText("");

        /*
         * Do not turn an unfinished payment into a wallet credit.
         */
        if (!"success".equalsIgnoreCase(
                paystackStatus
        )) {

            return PaymentResponse.builder()
                    .reference(reference)
                    .amount(
                            localTransaction.getAmount()
                    )
                    .status(
                            paystackStatus.isBlank()
                                    ? "PENDING"
                                    : paystackStatus.toUpperCase()
                    )
                    .build();
        }

        String currency =
                data.path("currency")
                        .asText("");

        if (!"NGN".equalsIgnoreCase(
                currency
        )) {

            throw new IllegalArgumentException(
                    "Unexpected payment currency"
            );
        }

        long paystackAmountKobo =
                data.path("amount")
                        .asLong(0);

        if (paystackAmountKobo <= 0) {

            throw new IllegalArgumentException(
                    "Invalid Paystack amount"
            );
        }

        BigDecimal verifiedAmount =
                BigDecimal.valueOf(
                        paystackAmountKobo,
                        2
                );

        if (verifiedAmount.compareTo(
                localTransaction.getAmount()
        ) != 0) {

            throw new IllegalArgumentException(
                    "Payment amount mismatch"
            );
        }

        String providerTransactionId =
                data.path("id")
                        .asText(null);

        if (providerTransactionId == null ||
                providerTransactionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack transaction ID is missing"
            );
        }

        /*
         * The settlement service performs:
         *
         * - PaymentTransaction lock
         * - Wallet lock
         * - duplicate protection
         * - double-entry ledger
         * - balance projection
         * - SUCCESSFUL state
         */
        paymentSettlementService
                .settleSuccessfulPaystackPayment(
                        user.getId(),
                        reference,
                        providerTransactionId,
                        verifiedAmount,
                        currency.toUpperCase(),
                        response.getBody()
                );

        Wallet wallet =
                walletRepository
                        .findByUserId(
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        return PaymentResponse.builder()
                .reference(reference)
                .amount(
                        verifiedAmount
                )
                .status("SUCCESSFUL")
                .walletBalance(
                        wallet.getBalance()
                )
                .build();
    }

    private BigDecimal normalizeNairaAmount(
            BigDecimal rawAmount
    ) {

        if (rawAmount == null) {

            throw new IllegalArgumentException(
                    "Payment amount is required"
            );
        }

        if (rawAmount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        try {

            return rawAmount.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );

        } catch (ArithmeticException e) {

            throw new IllegalArgumentException(
                    "Payment amount must not contain more than 2 decimal places"
            );
        }
    }

    private JsonNode parseJson(
            String responseBody
    ) {

        try {

            return objectMapper.readTree(
                    responseBody
            );

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "Invalid JSON response from Paystack",
                    e
            );
        }
    }

    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new IllegalArgumentException(
                    "Authentication required"
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            throw new IllegalArgumentException(
                    "Invalid authentication"
            );
        }

        return userRepository
                .findById(
                        user.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );
    }
}