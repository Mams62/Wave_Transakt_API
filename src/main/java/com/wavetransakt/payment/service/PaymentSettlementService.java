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
     * Atomically settle one VERIFIED Paystack payment.
     *
     * The Paystack HTTP request has already completed
     * before entering this method.
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

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment reference is required"
            );
        }

        if (providerTransactionId == null ||
                providerTransactionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Paystack transaction ID is missing"
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

        /*
         * ========================================================
         * LOCK PAYMENT TRANSACTION
         * ========================================================
         *
         * Two simultaneous verification requests for the same
         * payment cannot pass this section concurrently.
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

        /*
         * Another request may have completed while this
         * request waited for the lock.
         */
        if (transaction.getStatus() ==
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
         * Validate against OUR stored transaction again.
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
         * Prevent one Paystack provider transaction ID
         * from funding two local PaymentTransactions.
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
         * ========================================================
         * LOCK WALLET
         * ========================================================
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

        if (!wallet
                .getCurrency()
                .equalsIgnoreCase(
                        currency
                )) {

            throw new IllegalArgumentException(
                    "Wallet currency mismatch"
            );
        }

        /*
         * ========================================================
         * DOUBLE-ENTRY LEDGER
         * ========================================================
         *
         * For ₦10,000 funding:
         *
         * DEBIT  Paystack Settlement Asset    ₦10,000
         * CREDIT User Wallet Liability        ₦10,000
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
         * ========================================================
         * BALANCE PROJECTION
         * ========================================================
         */

        wallet.setBalance(
                wallet.getBalance()
                        .add(
                                verifiedAmount
                        )
        );

        walletRepository.save(wallet);

        /*
         * Mark successful only after ledger and wallet
         * projection have both been written.
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
}