package com.wavetransakt.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavetransakt.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaystackWebhookService {

    private static final Pattern SHA512_HEX =
            Pattern.compile(
                    "^[0-9a-fA-F]{128}$"
            );

    private final ObjectMapper
            objectMapper;

    private final PaymentWebhookEventRepository
            paymentWebhookEventRepository;

    private final PaymentSettlementService
            paymentSettlementService;

    @Value("${paystack.secret-key}")
    private String secretKey;

    /**
     * Handle one Paystack webhook atomically.
     */
    @Transactional
    public void processWebhook(
            String rawBody,
            String signature
    ) {

        if (rawBody == null ||
                rawBody.isBlank()) {

            throw new IllegalArgumentException(
                    "Webhook body is required"
            );
        }

        if (!isValidSignature(
                rawBody,
                signature
        )) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Paystack webhook signature"
            );
        }

        JsonNode root =
                parseJson(
                        rawBody
                );

        String eventType =
                root.path("event")
                        .asText("");

        /*
         * We acknowledge unrelated Paystack event types
         * without allowing them to create wallet value.
         */
        if (!"charge.success".equals(
                eventType
        )) {

            return;
        }

        JsonNode data =
                root.path("data");

        String paymentStatus =
                data.path("status")
                        .asText("");

        if (!"success".equalsIgnoreCase(
                paymentStatus
        )) {

            throw new IllegalArgumentException(
                    "Invalid charge.success payment status"
            );
        }

        String reference =
                data.path("reference")
                        .asText(null);

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack webhook reference is missing"
            );
        }

        String currency =
                data.path("currency")
                        .asText("");

        if (!"NGN".equalsIgnoreCase(
                currency
        )) {

            throw new IllegalArgumentException(
                    "Unexpected Paystack webhook currency"
            );
        }

        long amountKobo =
                data.path("amount")
                        .asLong(0);

        if (amountKobo <= 0) {

            throw new IllegalArgumentException(
                    "Invalid Paystack webhook amount"
            );
        }

        BigDecimal amount =
                BigDecimal.valueOf(
                        amountKobo,
                        2
                );

        String providerTransactionId =
                data.path("id")
                        .asText(null);

        if (providerTransactionId == null ||
                providerTransactionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack webhook transaction ID is missing"
            );
        }

        /*
         * One Paystack transaction can create only one
         * charge.success webhook processing event.
         */
        String eventKey =
                "PAYSTACK:" +
                        eventType +
                        ":" +
                        providerTransactionId;

        String payloadHash =
                sha256(
                        rawBody
                );

        /*
         * Atomic replay reservation.
         *
         * 1 = first time we have seen this event.
         * 0 = duplicate/retry already processed or processing.
         */
        int inserted =
                paymentWebhookEventRepository
                        .insertIfAbsent(
                                eventKey,
                                eventType,
                                reference,
                                providerTransactionId,
                                payloadHash
                        );

        if (inserted == 0) {

            return;
        }

        /*
         * The settlement service locks both the payment
         * transaction and wallet before any financial mutation.
         */
        paymentSettlementService
                .settleSuccessfulPaystackWebhook(
                        reference,
                        providerTransactionId,
                        amount,
                        currency.toUpperCase(
                                Locale.ROOT
                        ),
                        rawBody
                );

        int updated =
                paymentWebhookEventRepository
                        .markProcessed(
                                eventKey
                        );

        if (updated != 1) {

            throw new IllegalStateException(
                    "Unable to mark Paystack webhook as processed"
            );
        }
    }

    /**
     * Paystack signs the raw webhook request body using
     * HMAC-SHA512 and the secret key.
     */
    private boolean isValidSignature(
            String rawBody,
            String signature
    ) {

        if (signature == null ||
                !SHA512_HEX
                        .matcher(signature)
                        .matches()) {

            return false;
        }

        try {

            Mac mac =
                    Mac.getInstance(
                            "HmacSHA512"
                    );

            SecretKeySpec key =
                    new SecretKeySpec(
                            secretKey.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA512"
                    );

            mac.init(key);

            byte[] digest =
                    mac.doFinal(
                            rawBody.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            String expectedHex =
                    HexFormat.of()
                            .formatHex(
                                    digest
                            );

            /*
             * Constant-time comparison.
             */
            return MessageDigest.isEqual(
                    expectedHex
                            .getBytes(
                                    StandardCharsets.US_ASCII
                            ),
                    signature
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .getBytes(
                                    StandardCharsets.US_ASCII
                            )
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to verify Paystack webhook signature",
                    e
            );
        }
    }

    private JsonNode parseJson(
            String rawBody
    ) {

        try {

            return objectMapper.readTree(
                    rawBody
            );

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "Invalid Paystack webhook JSON",
                    e
            );
        }
    }

    private String sha256(
            String rawBody
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] result =
                    digest.digest(
                            rawBody.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(
                            result
                    );

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    e
            );
        }
    }
}