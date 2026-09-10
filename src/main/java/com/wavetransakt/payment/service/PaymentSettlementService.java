package com.wavetransakt.payment.service;

import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.payment.entity.PaymentTransaction;
import com.wavetransakt.payment.entity.PaymentTransactionStatus;
import com.wavetransakt.payment.repository.PaymentTransactionRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final PaymentTransactionRepository
            paymentTransactionRepository;

    private final WalletRepository
            walletRepository;

    private final LedgerService
            ledgerService;

    /**
     * Settlement initiated by an authenticated client
     * after calling Paystack's verify endpoint.
     */
    @Transactional
    public String settleSuccessfulPaystackPayment(
            UUID authenticatedUserId,
            String reference,
            String providerTransactionId,
            BigDecimal verifiedAmount,
            String currency,
            String providerResponse
    ) {

        if (authenticatedUserId == null) {

            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        return settle(
                authenticatedUserId,
                true,
                reference,
                providerTransactionId,
                verifiedAmount,
                currency,
                providerResponse
        );
    }

    /**
     * Settlement initiated by a verified Paystack webhook.
     *
     * No JWT user is required because webhook authenticity
     * is established using Paystack's HMAC signature.
     */
    @Transactional
    public String settleSuccessfulPaystackWebhook(
            String reference,
            String providerTransactionId,
            BigDecimal verifiedAmount,
            String currency,
            String providerResponse
    ) {

        return settle(
                null,
                false,
                reference,
                providerTransactionId,
                verifiedAmount,
                currency,
                providerResponse
        );
    }

    private String settle(
            UUID authenticatedUserId,
            boolean enforceUserOwnership,
            String reference,
            String providerTransactionId,
            BigDecimal verifiedAmount,
            String currency,
            String providerResponse
    ) {

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment reference is required"
            );
        }

        if (providerTransactionId == null ||
                providerTransactionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack transaction ID is required"
            );
        }

        if (verifiedAmount == null ||
                verifiedAmount.compareTo(
                        BigDecimal.ZERO
                ) <= 0) {

            throw new IllegalArgumentException(
                    "Verified payment amount is invalid"
            );
        }

        if (currency == null ||
                currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment currency is required"
            );
        }

        /*
         * --------------------------------------------------------
         * LOCK PAYMENT TRANSACTION
         * --------------------------------------------------------
         */
        PaymentTransaction transaction =
                paymentTransactionRepository
                        .findByReferenceForUpdate(
                                reference
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment transaction not found"
                                )
                        );

        if (!"PAYSTACK".equalsIgnoreCase(
                transaction.getProvider()
        )) {

            throw new IllegalArgumentException(
                    "Payment provider mismatch"
            );
        }

        if (enforceUserOwnership) {

            if (!transaction
                    .getUser()
                    .getId()
                    .equals(
                            authenticatedUserId
                    )) {

                throw new IllegalArgumentException(
                        "You are not authorized to verify this transaction"
                );
            }
        }

        /*
         * Always validate amount and currency even during
         * an idempotent replay.
         */
        if (verifiedAmount.compareTo(
                transaction.getAmount()
        ) != 0) {

            throw new IllegalArgumentException(
                    "Payment amount mismatch"
            );
        }

        if (!transaction
                .getCurrency()
                .equalsIgnoreCase(
                        currency
                )) {

            throw new IllegalArgumentException(
                    "Payment currency mismatch"
            );
        }

        /*
         * Another verification request or webhook may have
         * completed while this caller waited for the row lock.
         */
        if (transaction.getStatus() ==
                PaymentTransactionStatus.SUCCESSFUL) {

            String existingProviderId =
                    transaction
                            .getProviderTransactionId();

            if (existingProviderId == null ||
                    !existingProviderId.equals(
                            providerTransactionId
                    )) {

                throw new IllegalStateException(
                        "Successful payment provider transaction mismatch"
                );
            }

            return alreadyProcessedResponse(
                    reference
            );
        }

        /*
         * Provider transaction IDs are globally unique.
         *
         * V23 also enforces this at database level.
         */
        paymentTransactionRepository
                .findByProviderTransactionId(
                        providerTransactionId
                )
                .ifPresent(existing -> {

                    if (!existing
                            .getId()
                            .equals(
                                    transaction.getId()
                            )) {

                        throw new IllegalArgumentException(
                                "Paystack transaction has already been processed"
                        );
                    }
                });

        /*
         * --------------------------------------------------------
         * LOCK WALLET
         * --------------------------------------------------------
         */
        UUID walletId =
                transaction
                        .getWallet()
                        .getId();

        Wallet wallet =
                walletRepository
                        .findByIdForUpdate(
                                walletId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        if (wallet.getStatus() !=
                WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Wallet is not active"
            );
        }

        if (wallet.getBalance() == null) {

            throw new IllegalStateException(
                    "Wallet balance is unavailable"
            );
        }

        if (wallet.getCurrency() == null ||
                !wallet.getCurrency()
                        .equalsIgnoreCase(
                                currency
                        )) {

            throw new IllegalArgumentException(
                    "Wallet currency mismatch"
            );
        }

        /*
         * --------------------------------------------------------
         * DOUBLE ENTRY
         * --------------------------------------------------------
         *
         * DEBIT  Paystack Settlement Asset
         * CREDIT User Wallet Liability
         */
        String ledgerReference =
                "PAYSTACK:" +
                        transaction.getId();

        ledgerService.recordPaystackFunding(
                ledgerReference,
                wallet,
                verifiedAmount,
                currency,
                "Paystack wallet funding " +
                        reference
        );

        /*
         * wallet.balance remains the fast projection.
         */
        wallet.setBalance(
                wallet.getBalance()
                        .add(
                                verifiedAmount
                        )
        );

        walletRepository.save(
                wallet
        );

        /*
         * Payment becomes successful only after the ledger
         * and balance projection have both succeeded.
         */
        transaction.setProviderTransactionId(
                providerTransactionId
        );

        transaction.setStatus(
                PaymentTransactionStatus.SUCCESSFUL
        );

        paymentTransactionRepository.save(
                transaction
        );

        return providerResponse;
    }

    private String alreadyProcessedResponse(
            String reference
    ) {

        return """
                {
                  "message": "Payment already processed",
                  "reference": "%s",
                  "status": "SUCCESSFUL"
                }
                """.formatted(reference);
    }
}