package com.wavetransakt.ledger.service;

import com.wavetransakt.ledger.entity.*;
import com.wavetransakt.ledger.repository.LedgerAccountRepository;
import com.wavetransakt.ledger.repository.LedgerEntryRepository;
import com.wavetransakt.ledger.repository.LedgerTransactionRepository;
import com.wavetransakt.wallet.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderFundingLedgerServiceTest {

    @Mock LedgerAccountRepository accountRepository;
    @Mock LedgerTransactionRepository transactionRepository;
    @Mock LedgerEntryRepository entryRepository;
    @Mock LedgerService ledgerService;

    @Test
    void missingProviderSettlementAccountFailsClosed() {
        ProviderFundingLedgerService service = service();
        Wallet wallet = wallet();

        when(transactionRepository.existsByReference("PROVIDER_FUNDING:test")).thenReturn(false);
        when(accountRepository.findByCode("SYSTEM:PROVIDER_SETTLEMENT:TESTPROVIDER:NGN"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> service.recordProviderFunding(
                        "PROVIDER_FUNDING:test",
                        "TESTPROVIDER",
                        wallet,
                        new BigDecimal("25.00"),
                        "NGN",
                        "test"
                )
        );

        verifyNoInteractions(entryRepository, ledgerService);
    }

    @Test
    void providerFundingCreatesBalancedAssetToWalletLiabilityJournal() {
        ProviderFundingLedgerService service = service();
        Wallet wallet = wallet();

        LedgerAccount settlement = LedgerAccount.builder()
                .id(UUID.randomUUID())
                .code("SYSTEM:PROVIDER_SETTLEMENT:TESTPROVIDER:NGN")
                .accountType(LedgerAccountType.SYSTEM)
                .accountClass(LedgerAccountClass.ASSET)
                .currency("NGN")
                .build();
        LedgerAccount walletAccount = LedgerAccount.builder()
                .id(UUID.randomUUID())
                .code("WALLET:" + wallet.getId())
                .accountType(LedgerAccountType.WALLET)
                .accountClass(LedgerAccountClass.LIABILITY)
                .wallet(wallet)
                .currency("NGN")
                .build();
        LedgerTransaction journal = LedgerTransaction.builder()
                .reference("PROVIDER_FUNDING:test")
                .eventType(LedgerEventType.PROVIDER_FUNDING)
                .currency("NGN")
                .build();

        when(transactionRepository.existsByReference("PROVIDER_FUNDING:test")).thenReturn(false);
        when(accountRepository.findByCode("SYSTEM:PROVIDER_SETTLEMENT:TESTPROVIDER:NGN"))
                .thenReturn(Optional.of(settlement));
        when(ledgerService.ensureWalletAccount(wallet)).thenReturn(walletAccount);
        when(transactionRepository.save(any(LedgerTransaction.class))).thenReturn(journal);
        when(entryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordProviderFunding(
                "PROVIDER_FUNDING:test",
                "testprovider",
                wallet,
                new BigDecimal("25.00"),
                "ngn",
                "test provider funding"
        );

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        LedgerEntry debit = captor.getAllValues().get(0);
        LedgerEntry credit = captor.getAllValues().get(1);

        assertEquals(LedgerDirection.DEBIT, debit.getDirection());
        assertSame(settlement, debit.getAccount());
        assertEquals(new BigDecimal("25.00"), debit.getAmount());
        assertEquals(LedgerDirection.CREDIT, credit.getDirection());
        assertSame(walletAccount, credit.getAccount());
        assertEquals(debit.getAmount(), credit.getAmount());
    }

    private ProviderFundingLedgerService service() {
        return new ProviderFundingLedgerService(
                accountRepository,
                transactionRepository,
                entryRepository,
                ledgerService
        );
    }

    private Wallet wallet() {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .walletNumber("WT1234567890")
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .build();
    }
}
