package com.wavetransakt.ledger.service;

import com.wavetransakt.ledger.entity.LedgerAccount;
import com.wavetransakt.ledger.entity.LedgerAccountClass;
import com.wavetransakt.ledger.entity.LedgerAccountType;
import com.wavetransakt.ledger.entity.LedgerDirection;
import com.wavetransakt.ledger.entity.LedgerEntry;
import com.wavetransakt.ledger.entity.LedgerEventType;
import com.wavetransakt.ledger.entity.LedgerTransaction;
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

    private static final String PAYSTACK_SETTLEMENT_ACCOUNT =
            "SYSTEM:PAYSTACK_SETTLEMENT:NGN";

    private final LedgerAccountRepository
            ledgerAccountRepository;

    private final LedgerTransactionRepository
            ledgerTransactionRepository;

    private final LedgerEntryRepository
            ledgerEntryRepository;

    /**
     * ============================================================
     * ENSURE WALLET LEDGER ACCOUNT
     * ============================================================
     *
     * Every Wave Transakt wallet must have exactly one
     * corresponding ledger liability account.
     *
     * Wallet balances represent money that Wave Transakt
     * owes to the wallet owner, therefore wallet accounts
     * are LIABILITY accounts.
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

        if (wallet.getCurrency() == null ||
                wallet.getCurrency().isBlank()) {

            throw new IllegalArgumentException(
                    "Wallet currency is required"
            );
        }

        return ledgerAccountRepository
                .findByWalletId(
                        wallet.getId()
                )
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
     * ============================================================
     * WALLET TO WALLET TRANSFER JOURNAL
     * ============================================================
     *
     * Sender wallet liability decreases:
     *
     *      DEBIT sender
     *
     * Receiver wallet liability increases:
     *
     *      CREDIT receiver
     *
     * Example:
     *
     *      DEBIT  Sender Wallet      5,000
     *      CREDIT Receiver Wallet    5,000
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

        validateReference(reference);

        if (sender == null ||
                receiver == null) {

            throw new IllegalArgumentException(
                    "Sender and receiver are required"
            );
        }

        if (sender.getId() == null ||
                receiver.getId() == null) {

            throw new IllegalArgumentException(
                    "Wallet identifiers are required"
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
                normalizeAmount(
                        rawAmount
                );

        String normalizedCurrency =
                normalizeCurrency(
                        currency
                );

        /*
         * A financial transaction reference may only
         * create one ledger journal.
         *
         * Returning here is intentional for an
         * idempotent wallet-transfer replay.
         */
        if (ledgerTransactionRepository
                .existsByReference(reference)) {

            return;
        }

        LedgerAccount senderAccount =
                ensureWalletAccount(sender);

        LedgerAccount receiverAccount =
                ensureWalletAccount(receiver);

        validateAccountCurrency(
                senderAccount,
                normalizedCurrency
        );

        validateAccountCurrency(
                receiverAccount,
                normalizedCurrency
        );

        LedgerTransaction journal =
                ledgerTransactionRepository.save(
                        LedgerTransaction.builder()
                                .reference(reference)
                                .eventType(
                                        LedgerEventType.WALLET_TRANSFER
                                )
                                .currency(
                                        normalizedCurrency
                                )
                                .description(
                                        normalizeDescription(
                                                description
                                        )
                                )
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
                        .currency(
                                normalizedCurrency
                        )
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
                        .currency(
                                normalizedCurrency
                        )
                        .build();

        validateBalanced(
                debit.getAmount(),
                credit.getAmount()
        );

        ledgerEntryRepository.save(
                debit
        );

        ledgerEntryRepository.save(
                credit
        );
    }

    /**
     * ============================================================
     * PAYSTACK FUNDING JOURNAL
     * ============================================================
     *
     * Verified Paystack wallet funding:
     *
     *      DEBIT  Paystack Settlement Asset
     *      CREDIT User Wallet Liability
     *
     * Example:
     *
     *      DEBIT  PAYSTACK_SETTLEMENT    10,000
     *      CREDIT USER_WALLET            10,000
     *
     * This method must be called inside the same settlement
     * transaction that updates wallet.balance and marks the
     * PaymentTransaction SUCCESSFUL.
     */
    @Transactional
    public void recordPaystackFunding(
            String ledgerReference,
            Wallet wallet,
            BigDecimal rawAmount,
            String currency,
            String description
    ) {

        validateReference(
                ledgerReference
        );

        if (wallet == null ||
                wallet.getId() == null) {

            throw new IllegalArgumentException(
                    "Wallet is required"
            );
        }

        BigDecimal amount =
                normalizeAmount(
                        rawAmount
                );

        String normalizedCurrency =
                normalizeCurrency(
                        currency
                );

        /*
         * Unlike wallet transfer retries, reaching this point
         * for a Paystack settlement while the journal already
         * exists indicates an accounting/state inconsistency.
         *
         * The PaymentTransaction lock should prevent normal
         * duplicate settlement attempts from getting here.
         */
        if (ledgerTransactionRepository
                .existsByReference(
                        ledgerReference
                )) {

            throw new IllegalStateException(
                    "Paystack ledger journal already exists"
            );
        }

        LedgerAccount paystackSettlement =
                ledgerAccountRepository
                        .findByCode(
                                PAYSTACK_SETTLEMENT_ACCOUNT
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Paystack settlement ledger account is missing"
                                )
                        );

        LedgerAccount walletAccount =
                ensureWalletAccount(
                        wallet
                );

        validateAccountCurrency(
                paystackSettlement,
                normalizedCurrency
        );

        validateAccountCurrency(
                walletAccount,
                normalizedCurrency
        );

        LedgerTransaction journal =
                ledgerTransactionRepository.save(
                        LedgerTransaction.builder()
                                .reference(
                                        ledgerReference
                                )
                                .eventType(
                                        LedgerEventType.PAYSTACK_FUNDING
                                )
                                .currency(
                                        normalizedCurrency
                                )
                                .description(
                                        normalizeDescription(
                                                description
                                        )
                                )
                                .build()
                );

        /*
         * Paystack settlement asset increases.
         */
        LedgerEntry debit =
                LedgerEntry.builder()
                        .ledgerTransaction(
                                journal
                        )
                        .account(
                                paystackSettlement
                        )
                        .lineNo(
                                (short) 1
                        )
                        .direction(
                                LedgerDirection.DEBIT
                        )
                        .amount(
                                amount
                        )
                        .currency(
                                normalizedCurrency
                        )
                        .build();

        /*
         * Wallet liability increases.
         */
        LedgerEntry credit =
                LedgerEntry.builder()
                        .ledgerTransaction(
                                journal
                        )
                        .account(
                                walletAccount
                        )
                        .lineNo(
                                (short) 2
                        )
                        .direction(
                                LedgerDirection.CREDIT
                        )
                        .amount(
                                amount
                        )
                        .currency(
                                normalizedCurrency
                        )
                        .build();

        validateBalanced(
                debit.getAmount(),
                credit.getAmount()
        );

        ledgerEntryRepository.save(
                debit
        );

        ledgerEntryRepository.save(
                credit
        );
    }

    /**
     * ============================================================
     * WALLET LEDGER BALANCE
     * ============================================================
     *
     * Wallet accounts are liabilities.
     *
     * Balance =
     *
     *      total credits - total debits
     */
    @Transactional(readOnly = true)
    public BigDecimal getWalletLedgerBalance(
            UUID walletId
    ) {

        if (walletId == null) {

            throw new IllegalArgumentException(
                    "Wallet ID is required"
            );
        }

        LedgerAccount account =
                ledgerAccountRepository
                        .findByWalletId(
                                walletId
                        )
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

        if (balance == null) {
            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.UNNECESSARY
                    );
        }

        return balance.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    /**
     * ============================================================
     * BALANCING INVARIANT
     * ============================================================
     *
     * Every journal must satisfy:
     *
     *      total debit == total credit
     */
    private void validateBalanced(
            BigDecimal totalDebit,
            BigDecimal totalCredit
    ) {

        if (totalDebit == null ||
                totalCredit == null) {

            throw new IllegalStateException(
                    "Ledger totals are unavailable"
            );
        }

        if (totalDebit.compareTo(
                totalCredit
        ) != 0) {

            throw new IllegalStateException(
                    "Ledger transaction is not balanced"
            );
        }
    }

    /**
     * ============================================================
     * REFERENCE VALIDATION
     * ============================================================
     */
    private void validateReference(
            String reference
    ) {

        if (reference == null ||
                reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Ledger reference is required"
            );
        }

        if (reference.trim().length() > 100) {

            throw new IllegalArgumentException(
                    "Ledger reference must not exceed 100 characters"
            );
        }
    }

    /**
     * ============================================================
     * AMOUNT NORMALIZATION
     * ============================================================
     */
    private BigDecimal normalizeAmount(
            BigDecimal amount
    ) {

        if (amount == null) {

            throw new IllegalArgumentException(
                    "Ledger amount is required"
            );
        }

        if (amount.compareTo(
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

    /**
     * ============================================================
     * CURRENCY NORMALIZATION
     * ============================================================
     */
    private String normalizeCurrency(
            String currency
    ) {

        if (currency == null ||
                currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }

        String normalized =
                currency.trim()
                        .toUpperCase();

        if (normalized.length() != 3) {

            throw new IllegalArgumentException(
                    "Invalid currency"
            );
        }

        return normalized;
    }

    /**
     * ============================================================
     * ACCOUNT CURRENCY CHECK
     * ============================================================
     */
    private void validateAccountCurrency(
            LedgerAccount account,
            String currency
    ) {

        if (account == null) {

            throw new IllegalStateException(
                    "Ledger account is unavailable"
            );
        }

        if (account.getCurrency() == null ||
                !account.getCurrency()
                        .equalsIgnoreCase(
                                currency
                        )) {

            throw new IllegalArgumentException(
                    "Ledger account currency mismatch"
            );
        }
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
                    "Ledger description must not exceed 255 characters"
            );
        }

        return normalized;
    }
}