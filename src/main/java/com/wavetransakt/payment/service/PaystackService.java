package com.wavetransakt.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavetransakt.payment.dto.InitializePaymentRequest;
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

@Service
@RequiredArgsConstructor
public class PaystackService {

    private static final String BASE_URL =
            "https://api.paystack.co/transaction/";

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
     * INITIALIZE PAYSTACK FUNDING
     * ============================================================
     */
    public String initializeTransaction(
            InitializePaymentRequest request,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
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

        if (wallet.getStatus() !=
                WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Wallet is not available for funding"
            );
        }

        if (request == null) {

            throw new IllegalArgumentException(
                    "Payment request is required"
            );
        }

        if (request.getEmail() == null ||
                !user.getEmail()
                        .equalsIgnoreCase(
                                request.getEmail()
                        )) {

            throw new IllegalArgumentException(
                    "Payment email must match the authenticated account"
            );
        }

        if (request.getAmount() <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
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

        String body =
                """
                {
                    "email": "%s",
                    "amount": %d,
                    "currency": "NGN"
                }
                """.formatted(
                        request.getEmail(),
                        request.getAmount()
                );

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

        try {

            JsonNode root =
                    parseJson(
                            response.getBody()
                    );

            boolean status =
                    root.path("status")
                            .asBoolean(false);

            if (!status) {

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

            if (reference == null ||
                    reference.isBlank()) {

                throw new IllegalArgumentException(
                        "Paystack did not return a transaction reference"
                );
            }

            if (paymentTransactionRepository
                    .existsByReference(reference)) {

                throw new IllegalArgumentException(
                        "Duplicate Paystack payment reference"
                );
            }

            /*
             * Paystack request amount is kobo.
             * Store local wallet amount in naira.
             */
            BigDecimal amountNaira =
                    BigDecimal.valueOf(
                            request.getAmount(),
                            2
                    );

            PaymentTransaction transaction =
                    PaymentTransaction.builder()
                            .user(user)
                            .wallet(wallet)
                            .reference(reference)
                            .amount(amountNaira)
                            .currency("NGN")
                            .provider("PAYSTACK")
                            .status(
                                    PaymentTransactionStatus.PENDING
                            )
                            .build();

            paymentTransactionRepository.save(
                    transaction
            );

            return response.getBody();

        } catch (Exception e) {

            if (e instanceof IllegalArgumentException) {
                throw e;
            }

            throw new IllegalArgumentException(
                    "Unable to process Paystack response",
                    e
            );
        }
    }

    /**
     * ============================================================
     * VERIFY PAYSTACK FUNDING
     * ============================================================
     *
     * IMPORTANT:
     *
     * This method itself is NOT @Transactional.
     *
     * We first obtain Paystack's response without holding
     * database locks.
     *
     * Only the local settlement phase acquires locks.
     */
    public String verifyTransaction(
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

        /*
         * Fast ownership/idempotency check before making
         * another external Paystack request.
         */
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

        if (localTransaction.getStatus() ==
                PaymentTransactionStatus.SUCCESSFUL) {

            return """
                    {
                      "message": "Payment already processed",
                      "reference": "%s",
                      "status": "SUCCESSFUL"
                    }
                    """.formatted(reference);
        }

        /*
         * ========================================================
         * VERIFY DIRECTLY WITH PAYSTACK
         * ========================================================
         */

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

        /*
         * No wallet mutation for an unsuccessful provider
         * verification response.
         */
        if (!apiStatus) {

            return response.getBody();
        }

        JsonNode data =
                root.path("data");

        String providerReference =
                data.path("reference")
                        .asText("");

        /*
         * Never accept a Paystack response for a different
         * transaction reference.
         */
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
         * Only SUCCESS creates wallet value.
         *
         * Pending/abandoned/failed responses do not credit
         * anything here.
         */
        if (!"success".equalsIgnoreCase(
                paystackStatus
        )) {

            return response.getBody();
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
         * ========================================================
         * ATOMIC LOCAL SETTLEMENT
         * ========================================================
         */

        return paymentSettlementService
                .settleSuccessfulPaystackPayment(
                        user.getId(),
                        reference,
                        providerTransactionId,
                        verifiedAmount,
                        currency.toUpperCase(),
                        response.getBody()
                );
    }

    /**
     * Parse Paystack JSON safely.
     */
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

    /**
     * Resolve authenticated Wave Transakt user.
     */
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