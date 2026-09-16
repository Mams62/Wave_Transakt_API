package com.wavetransakt.wallet.funding;

import com.wavetransakt.ledger.service.ProviderFundingLedgerService;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerFundingCreditServiceTest {

    @Mock CustomerFundingEventRepository eventRepository;
    @Mock WalletRepository walletRepository;
    @Mock ProviderFundingLedgerService ledgerService;

    @Test
    void globalSwitchFailsClosedBeforeReadingFinancialState() {
        CustomerFundingCreditService service = new CustomerFundingCreditService(
                eventRepository, walletRepository, ledgerService, List.of(), false
        );

        assertThrows(IllegalStateException.class, () -> service.attemptCredit(UUID.randomUUID()));
        verifyNoInteractions(eventRepository, walletRepository, ledgerService);
    }

    @Test
    void missingProviderPolicyMovesVerifiedEventToReconciliation() {
        UUID eventId = UUID.randomUUID();
        CustomerFundingEvent event = verifiedEvent(eventId, activeFundingAccount());
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        CustomerFundingCreditService service = new CustomerFundingCreditService(
                eventRepository, walletRepository, ledgerService, List.of(), true
        );

        CustomerFundingCreditService.CreditResult result = service.attemptCredit(eventId);

        assertFalse(result.credited());
        assertEquals(CustomerFundingEventStatus.RECONCILIATION_REQUIRED, event.getStatus());
        assertEquals("UNCONFIGURED", event.getCreditPolicyCode());
        assertEquals("RECONCILIATION_REQUIRED", event.getCreditDecision());
        verifyNoInteractions(walletRepository, ledgerService);
    }

    @Test
    void approvedPolicyStillRequiresConfiguredSettlementLedgerAccount() {
        UUID eventId = UUID.randomUUID();
        CustomerFundingEvent event = verifiedEvent(eventId, activeFundingAccount());
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(ledgerService.hasSettlementAccount("TESTPROVIDER", "NGN")).thenReturn(false);

        CustomerFundingCreditService service = new CustomerFundingCreditService(
                eventRepository,
                walletRepository,
                ledgerService,
                List.of(allowPolicy()),
                true
        );

        CustomerFundingCreditService.CreditResult result = service.attemptCredit(eventId);

        assertFalse(result.credited());
        assertEquals(CustomerFundingEventStatus.RECONCILIATION_REQUIRED, event.getStatus());
        verify(ledgerService, never()).recordProviderFunding(anyString(), anyString(), any(), any(), anyString(), anyString());
        verifyNoInteractions(walletRepository);
    }

    @Test
    void approvedPolicyAndSettlementAccountCreditAtomicallyThroughLedgerBoundary() {
        UUID eventId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .walletNumber("WT1234567890")
                .balance(new BigDecimal("100.00"))
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .build();
        CustomerFundingAccount account = activeFundingAccount();
        account.setWallet(wallet);
        CustomerFundingEvent event = verifiedEvent(eventId, account);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(ledgerService.hasSettlementAccount("TESTPROVIDER", "NGN")).thenReturn(true);
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.save(wallet)).thenReturn(wallet);
        when(eventRepository.save(event)).thenReturn(event);

        CustomerFundingCreditService service = new CustomerFundingCreditService(
                eventRepository,
                walletRepository,
                ledgerService,
                List.of(allowPolicy()),
                true
        );

        CustomerFundingCreditService.CreditResult result = service.attemptCredit(eventId);

        assertTrue(result.credited());
        assertFalse(result.idempotent());
        assertEquals(CustomerFundingEventStatus.CREDITED, event.getStatus());
        assertEquals(new BigDecimal("150.00"), wallet.getBalance());
        assertNotNull(event.getCreditedAt());
        assertEquals("PROVIDER_FUNDING:" + eventId, event.getLedgerReference());
        verify(ledgerService).recordProviderFunding(
                eq("PROVIDER_FUNDING:" + eventId),
                eq("TESTPROVIDER"),
                same(wallet),
                eq(new BigDecimal("50.00")),
                eq("NGN"),
                contains("TESTPROVIDER")
        );
    }

    @Test
    void creditedEventIsIdempotentAndNeverPostsTwice() {
        UUID eventId = UUID.randomUUID();
        CustomerFundingEvent event = verifiedEvent(eventId, activeFundingAccount());
        event.setStatus(CustomerFundingEventStatus.CREDITED);
        event.setLedgerReference("PROVIDER_FUNDING:" + eventId);
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        CustomerFundingCreditService service = new CustomerFundingCreditService(
                eventRepository, walletRepository, ledgerService, List.of(), true
        );

        CustomerFundingCreditService.CreditResult result = service.attemptCredit(eventId);

        assertTrue(result.credited());
        assertTrue(result.idempotent());
        verifyNoInteractions(walletRepository, ledgerService);
    }

    private FundingCreditPolicy allowPolicy() {
        return new FundingCreditPolicy() {
            @Override public String providerCode() { return "TESTPROVIDER"; }
            @Override public String policyCode() { return "TEST_FINALITY_V1"; }
            @Override public CreditDecision evaluate(CustomerFundingEvent event) {
                return CreditDecision.allow("Test-only finality approval");
            }
        };
    }

    private CustomerFundingAccount activeFundingAccount() {
        return CustomerFundingAccount.builder()
                .id(UUID.randomUUID())
                .providerCode("TESTPROVIDER")
                .status(CustomerFundingAccountStatus.ACTIVE)
                .build();
    }

    private CustomerFundingEvent verifiedEvent(UUID eventId, CustomerFundingAccount account) {
        return CustomerFundingEvent.builder()
                .id(eventId)
                .fundingAccount(account)
                .providerCode("TESTPROVIDER")
                .providerEventId("evt-" + eventId)
                .status(CustomerFundingEventStatus.VERIFIED)
                .amount(new BigDecimal("50.00"))
                .currency("NGN")
                .build();
    }
}
