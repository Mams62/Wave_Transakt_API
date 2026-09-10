package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.ledger.entity.LedgerAccount;
import com.wavetransakt.ledger.entity.LedgerDirection;
import com.wavetransakt.ledger.entity.LedgerEntry;
import com.wavetransakt.ledger.entity.LedgerEventType;
import com.wavetransakt.ledger.entity.LedgerTransaction;
import com.wavetransakt.ledger.repository.LedgerAccountRepository;
import com.wavetransakt.ledger.repository.LedgerEntryRepository;
import com.wavetransakt.ledger.repository.LedgerTransactionRepository;
import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.wallet.entity.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class ServicePaymentLedgerService {

    private static final String VTPASS_SETTLEMENT_ACCOUNT =
            "SYSTEM:VTPASS_SETTLEMENT:NGN";

    private final LedgerService ledgerService;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public void recordReservation(
            String reference,
            Wallet wallet,
            BigDecimal rawAmount,
            String description
    ) {
        BigDecimal amount = normalizeAmount(rawAmount);

        if (ledgerTransactionRepository.existsByReference(reference)) {
            return;
        }

        LedgerAccount walletAccount = ledgerService.ensureWalletAccount(wallet);
        LedgerAccount providerAccount = providerAccount();
        validateCurrency(walletAccount, wallet.getCurrency());
        validateCurrency(providerAccount, wallet.getCurrency());

        LedgerTransaction journal = ledgerTransactionRepository.save(
                LedgerTransaction.builder()
                        .reference(reference)
                        .eventType(LedgerEventType.BILL_PAYMENT)
                        .currency(wallet.getCurrency())
                        .description(description)
                        .build()
        );

        ledgerEntryRepository.save(
                LedgerEntry.builder()
                        .ledgerTransaction(journal)
                        .account(walletAccount)
                        .lineNo((short) 1)
                        .direction(LedgerDirection.DEBIT)
                        .amount(amount)
                        .currency(wallet.getCurrency())
                        .build()
        );

        ledgerEntryRepository.save(
                LedgerEntry.builder()
                        .ledgerTransaction(journal)
                        .account(providerAccount)
                        .lineNo((short) 2)
                        .direction(LedgerDirection.CREDIT)
                        .amount(amount)
                        .currency(wallet.getCurrency())
                        .build()
        );
    }

    @Transactional
    public void recordReversal(
            String originalReference,
            Wallet wallet,
            BigDecimal rawAmount,
            String description
    ) {
        BigDecimal amount = normalizeAmount(rawAmount);
        String reversalReference = "REV:" + originalReference;

        if (ledgerTransactionRepository.existsByReference(reversalReference)) {
            return;
        }

        LedgerTransaction original = ledgerTransactionRepository
                .findByReference(originalReference)
                .orElseThrow(() -> new IllegalStateException(
                        "Original service payment ledger journal is missing"
                ));

        LedgerAccount walletAccount = ledgerService.ensureWalletAccount(wallet);
        LedgerAccount providerAccount = providerAccount();
        validateCurrency(walletAccount, wallet.getCurrency());
        validateCurrency(providerAccount, wallet.getCurrency());

        LedgerTransaction reversal = ledgerTransactionRepository.save(
                LedgerTransaction.builder()
                        .reference(reversalReference)
                        .eventType(LedgerEventType.REVERSAL)
                        .currency(wallet.getCurrency())
                        .description(description)
                        .reversalOf(original)
                        .build()
        );

        ledgerEntryRepository.save(
                LedgerEntry.builder()
                        .ledgerTransaction(reversal)
                        .account(providerAccount)
                        .lineNo((short) 1)
                        .direction(LedgerDirection.DEBIT)
                        .amount(amount)
                        .currency(wallet.getCurrency())
                        .build()
        );

        ledgerEntryRepository.save(
                LedgerEntry.builder()
                        .ledgerTransaction(reversal)
                        .account(walletAccount)
                        .lineNo((short) 2)
                        .direction(LedgerDirection.CREDIT)
                        .amount(amount)
                        .currency(wallet.getCurrency())
                        .build()
        );
    }

    private LedgerAccount providerAccount() {
        return ledgerAccountRepository
                .findByCode(VTPASS_SETTLEMENT_ACCOUNT)
                .orElseThrow(() -> new IllegalStateException(
                        "VTpass settlement ledger account is missing"
                ));
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Service payment amount must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void validateCurrency(LedgerAccount account, String currency) {
        if (currency == null || !currency.equalsIgnoreCase(account.getCurrency())) {
            throw new IllegalArgumentException("Ledger account currency mismatch");
        }
    }
}
