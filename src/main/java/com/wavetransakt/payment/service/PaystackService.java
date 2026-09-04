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
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaystackService {

    private final RestTemplate restTemplate;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    @Value("${paystack.secret-key}")
    private String secretKey;

    private static final String BASE_URL =
            "https://api.paystack.co/transaction/";

    /**
     * Initialize a wallet funding transaction.
     */
    @Transactional
    public String initializeTransaction(
            InitializePaymentRequest request,
            Authentication authentication
    ) {

        User user = getAuthenticatedUser(authentication);

        Wallet wallet = walletRepository
                .findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet not found"
                        )
                );

        if (wallet.getStatus().name().equals("SUSPENDED")
                || wallet.getStatus().name().equals("BLOCKED")
                || wallet.getStatus().name().equals("CLOSED")) {

            throw new IllegalArgumentException(
                    "Wallet is not available for funding"
            );
        }

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Payment email must match the authenticated account"
            );
        }

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
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
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        BASE_URL + "initialize",
                        HttpMethod.POST,
                        entity,
                        String.class
                );

        if (!response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null) {

            throw new IllegalArgumentException(
                    "Unable to initialize Paystack transaction"
            );
        }

        try {

            JsonNode root =
                    parseJson(response.getBody());

            boolean status =
                    root.path("status").asBoolean(false);

            if (!status) {

                throw new IllegalArgumentException(
                        root.path("message")
                                .asText(
                                        "Paystack initialization failed"
                                )
                );
            }

            JsonNode data = root.path("data");

            String reference =
                    data.path("reference").asText(null);

            if (reference == null || reference.isBlank()) {

                throw new IllegalArgumentException(
                        "Paystack did not return a transaction reference"
                );
            }

            PaymentTransaction transaction =
                    PaymentTransaction.builder()
                            .user(user)
                            .wallet(wallet)
                            .reference(reference)
                            .amount(
                                    BigDecimal.valueOf(
                                            request.getAmount()
                                    ).divide(
                                            BigDecimal.valueOf(100)
                                    )
                            )
                            .currency("NGN")
                            .provider("PAYSTACK")
                            .status(
                                    PaymentTransactionStatus.PENDING
                            )
                            .build();

            paymentTransactionRepository.save(transaction);

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
     * Verify a Paystack transaction and credit the wallet once.
     *
     * Paystack amounts are represented in kobo.
     * Wallet amounts are represented in naira.
     */
    @Transactional
    public String verifyTransaction(
            String reference,
            Authentication authentication
    ) {

        User user = getAuthenticatedUser(authentication);

        PaymentTransaction transaction =
                paymentTransactionRepository
                        .findByReference(reference)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment transaction not found"
                                )
                        );

        if (!transaction.getUser().getId()
                .equals(user.getId())) {

            throw new IllegalArgumentException(
                    "You are not authorized to verify this transaction"
            );
        }

        /*
         * Idempotency:
         *
         * If the transaction has already been successfully
         * processed, do NOT credit the wallet again.
         */
        if (transaction.getStatus()
                == PaymentTransactionStatus.SUCCESSFUL) {

            return """
                    {
                      "message": "Payment already processed",
                      "reference": "%s",
                      "status": "SUCCESSFUL"
                    }
                    """.formatted(reference);
        }

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(secretKey);

        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        BASE_URL + "verify/" + reference,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

        if (!response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null) {

            throw new IllegalArgumentException(
                    "Unable to verify Paystack transaction"
            );
        }

        try {

            JsonNode root =
                    parseJson(response.getBody());

            boolean status =
                    root.path("status").asBoolean(false);

            if (!status) {

                transaction.setStatus(
                        PaymentTransactionStatus.FAILED
                );

                paymentTransactionRepository.save(
                        transaction
                );

                return response.getBody();
            }

            JsonNode data = root.path("data");

            String paystackStatus =
                    data.path("status").asText("");

            long paystackAmount =
                    data.path("amount").asLong(0);

            String currency =
                    data.path("currency").asText("");

            String transactionId =
                    data.path("id").asText(null);

            /*
             * Only a successful Paystack transaction
             * can credit the wallet.
             */
            if (!"success".equalsIgnoreCase(paystackStatus)) {

                transaction.setStatus(
                        PaymentTransactionStatus.FAILED
                );

                paymentTransactionRepository.save(
                        transaction
                );

                return response.getBody();
            }

            /*
             * Confirm currency.
             */
            if (!"NGN".equalsIgnoreCase(currency)) {

                transaction.setStatus(
                        PaymentTransactionStatus.FAILED
                );

                paymentTransactionRepository.save(
                        transaction
                );

                throw new IllegalArgumentException(
                        "Unexpected payment currency"
                );
            }

            /*
             * Paystack amount is kobo.
             */
            BigDecimal verifiedAmount =
                    BigDecimal.valueOf(paystackAmount)
                            .divide(
                                    BigDecimal.valueOf(100)
                            );

            /*
             * Never trust the client amount.
             * Compare the amount Paystack actually
             * processed with our stored transaction.
             */
            if (verifiedAmount.compareTo(
                    transaction.getAmount()
            ) != 0) {

                transaction.setStatus(
                        PaymentTransactionStatus.FAILED
                );

                paymentTransactionRepository.save(
                        transaction
                );

                throw new IllegalArgumentException(
                        "Payment amount mismatch"
                );
            }

            /*
             * Get the wallet again from the database.
             */
            Wallet wallet =
                    walletRepository
                            .findByUserId(user.getId())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Wallet not found"
                                    )
                            );

            if (!wallet.getStatus().name()
                    .equals("ACTIVE")) {

                throw new IllegalArgumentException(
                        "Wallet is not active"
                );
            }

            /*
             * Credit wallet exactly once.
             */
            wallet.setBalance(
                    wallet.getBalance()
                            .add(verifiedAmount)
            );

            walletRepository.save(wallet);

            /*
             * Mark transaction successful only
             * after wallet credit has been persisted.
             */
            transaction.setStatus(
                    PaymentTransactionStatus.SUCCESSFUL
            );

            transaction.setProviderTransactionId(
                    transactionId
            );

            paymentTransactionRepository.save(
                    transaction
            );

            return response.getBody();

        } catch (Exception e) {

            if (e instanceof IllegalArgumentException) {
                throw e;
            }

            throw new IllegalArgumentException(
                    "Unable to process Paystack verification response",
                    e
            );
        }
    }

    /**
     * Safely parse a Paystack JSON response.
     */
    private JsonNode parseJson(String responseBody) {

        try {

            return objectMapper.readTree(responseBody);

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "Invalid JSON response from Paystack",
                    e
            );
        }
    }

    /**
     * Get the authenticated user from Spring Security.
     */
    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

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
                .findById(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );
    }
}