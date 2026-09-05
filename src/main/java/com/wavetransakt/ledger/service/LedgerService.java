package com.wavetransakt.ledger.service;

import com.wavetransakt.ledger.entity.*;
import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.ledger.repository.LedgerAccountRepository;
import com.wavetransakt.ledger.repository.LedgerEntryRepository;
import com.wavetransakt.ledger.repository.LedgerTransactionRepository;
import com.wavetransakt.wallet.entity.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerAccountRepository
            ledgerAccountRepository;

    private final LedgerTransactionRepository
            ledgerTransactionRepository;

    private final LedgerEntryRepository
            ledgerEntryRepository;

    /**
     * Every wallet must have exactly one ledger account.
     *
     * User wallet accounts are LIABILITY accounts because
     * wallet value represents money Wave Transakt owes
     * to the wallet owner.
     */
    @Transactional
    public LedgerAccount ensureWalletAccount(
            Wallet wallet
    ) {

        if (wallet == null ||
                wallet.getId() == null) {

            throw new IllegalArgumentException(
                    "Wallet is required"
            );
        }

        return ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseGet(() ->
                        ledgerAccountRepository.save(
                                LedgerAccount.builder()
                                        .code(
                                                "WALLET:" +
                                                        wallet.getId()
                                        )
                                        .accountType(
                                                LedgerAccountType.WALLET
                                        )
                                        .accountClass(
                                                LedgerAccountClass.LIABILITY
                                        )
                                        .wallet(wallet)
                                        .currency(
                                                wallet.getCurrency()
                                        )
                                        .build()
                        )
                );
    }

    /**
     * Record both sides of a wallet-to-wallet transfer.
     *
     * Sender wallet liability decreases -> DEBIT.
     * Receiver wallet liability increases -> CREDIT.
     */
    @Transactional
    public void recordWalletTransfer(
            String reference,
            Wallet sender,
            Wallet receiver,
            BigDecimal rawAmount,
            String currency,
            String description
    ) {

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Ledger reference is required"
            );
        }

        if (sender == null ||
                receiver == null) {

            throw new IllegalArgumentException(
                    "Sender and receiver are required"
            );
        }

        if (sender.getId().equals(
                receiver.getId()
        )) {

            throw new IllegalArgumentException(
                    "Ledger transfer requires different accounts"
            );
        }

        BigDecimal amount =
                normalizeAmount(rawAmount);

        if (currency == null ||
                currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }

        /*
         * Idempotent ledger posting.
         *
         * A financial transaction reference may only
         * create one ledger journal.
         */
        if (ledgerTransactionRepository
                .existsByReference(reference)) {

            return;
        }

        LedgerAccount senderAccount =
                ensureWalletAccount(sender);

        LedgerAccount receiverAccount =
                ensureWalletAccount(receiver);

        LedgerTransaction journal =
                ledgerTransactionRepository.save(
                        LedgerTransaction.builder()
                                .reference(reference)
                                .eventType(
                                        LedgerEventType.WALLET_TRANSFER
                                )
                                .currency(currency)
                                .description(description)
                                .build()
                );

        LedgerEntry debit =
                LedgerEntry.builder()
                        .ledgerTransaction(journal)
                        .account(senderAccount)
                        .lineNo((short) 1)
                        .direction(
                                LedgerDirection.DEBIT
                        )
                        .amount(amount)
                        .currency(currency)
                        .build();

        LedgerEntry credit =
                LedgerEntry.builder()
                        .ledgerTransaction(journal)
                        .account(receiverAccount)
                        .lineNo((short) 2)
                        .direction(
                                LedgerDirection.CREDIT
                        )
                        .amount(amount)
                        .currency(currency)
                        .build();

        /*
         * Explicit invariant before persistence.
         */
        validateBalanced(
                debit.getAmount(),
                credit.getAmount()
        );

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
    }

    @Transactional(readOnly = true)
    public BigDecimal getWalletLedgerBalance(
            UUID walletId
    ) {

        LedgerAccount account =
                ledgerAccountRepository
                        .findByWalletId(walletId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet ledger account not found"
                                )
                        );

        BigDecimal balance =
                ledgerEntryRepository
                        .calculateAccountBalance(
                                account.getId()
                        );

        return balance == null
                ? BigDecimal.ZERO
                : balance.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private void validateBalanced(
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {

        if (totalDebit.compareTo(
                totalCredit
        ) != 0) {

            throw new IllegalStateException(
                    "Ledger transaction is not balanced"
            );
        }
    }

    private BigDecimal normalizeAmount(
            BigDecimal amount
    ) {

        if (amount == null ||
                amount.compareTo(
                        BigDecimal.ZERO
                ) <= 0) {

            throw new IllegalArgumentException(
                    "Ledger amount must be greater than zero"
            );
        }

        try {

            return amount.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );

        } catch (ArithmeticException e) {

            throw new IllegalArgumentException(
                    "Ledger amount must not contain more than 2 decimal places"
            );
        }
    }
}