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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Provider-neutral double-entry posting for approved incoming funding.
 *
 * This service never creates provider settlement accounts. A provider-specific
 * migration must create the expected SYSTEM asset account after commercial and
 * settlement approval. Missing configuration therefore fails closed.
 */
@Service
public class ProviderFundingLedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;

    public ProviderFundingLedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerService ledgerService
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public boolean hasSettlementAccount(String providerCode, String currency) {
        return ledgerAccountRepository.findByCode(settlementAccountCode(providerCode, currency)).isPresent();
    }

    @Transactional
    public void recordProviderFunding(
            String ledgerReference,
            String providerCode,
            Wallet wallet,
            BigDecimal rawAmount,
            String currency,
            String description
    ) {
        String reference = normalizeRequired(ledgerReference, 100, "Ledger reference");
        String normalizedProvider = normalizeProvider(providerCode);
        String normalizedCurrency = normalizeCurrency(currency);
        BigDecimal amount = normalizeAmount(rawAmount);

        if (wallet == null || wallet.getId() == null) {
            throw new IllegalArgumentException("Wallet is required");
        }

        if (ledgerTransactionRepository.existsByReference(reference)) {
            throw new IllegalStateException("Provider funding ledger journal already exists");
        }

        LedgerAccount settlementAccount = ledgerAccountRepository
                .findByCode(settlementAccountCode(normalizedProvider, normalizedCurrency))
                .orElseThrow(() -> new IllegalStateException(
                        "Provider settlement ledger account is not configured"
                ));

        if (settlementAccount.getAccountType() != LedgerAccountType.SYSTEM ||
                settlementAccount.getAccountClass() != LedgerAccountClass.ASSET) {
            throw new IllegalStateException("Provider settlement ledger account is invalid");
        }

        validateCurrency(settlementAccount, normalizedCurrency);

        LedgerAccount walletAccount = ledgerService.ensureWalletAccount(wallet);
        validateCurrency(walletAccount, normalizedCurrency);

        LedgerTransaction journal = ledgerTransactionRepository.save(
                LedgerTransaction.builder()
                        .reference(reference)
                        .eventType(LedgerEventType.PROVIDER_FUNDING)
                        .currency(normalizedCurrency)
                        .description(normalizeOptional(description, 255))
                        .build()
        );

        LedgerEntry debit = LedgerEntry.builder()
                .ledgerTransaction(journal)
                .account(settlementAccount)
                .lineNo((short) 1)
                .direction(LedgerDirection.DEBIT)
                .amount(amount)
                .currency(normalizedCurrency)
                .build();

        LedgerEntry credit = LedgerEntry.builder()
                .ledgerTransaction(journal)
                .account(walletAccount)
                .lineNo((short) 2)
                .direction(LedgerDirection.CREDIT)
                .amount(amount)
                .currency(normalizedCurrency)
                .build();

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
    }

    private String settlementAccountCode(String providerCode, String currency) {
        return "SYSTEM:PROVIDER_SETTLEMENT:" + normalizeProvider(providerCode) + ":" + normalizeCurrency(currency);
    }

    private String normalizeProvider(String value) {
        String normalized = normalizeRequired(value, 40, "Provider code").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_\\-]+")) {
            throw new IllegalArgumentException("Invalid provider code");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        String normalized = normalizeRequired(value, 3, "Currency").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Invalid currency");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Funding amount must be greater than zero");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Funding amount must not contain more than 2 decimal places");
        }
    }

    private void validateCurrency(LedgerAccount account, String currency) {
        if (account.getCurrency() == null || !account.getCurrency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Ledger account currency mismatch");
        }
    }

    private String normalizeRequired(String value, int maxLength, String label) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Value is too long");
        }
        return normalized;
    }
}
