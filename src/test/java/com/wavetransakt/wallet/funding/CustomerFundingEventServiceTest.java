package com.wavetransakt.wallet.funding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerFundingEventServiceTest {

    @Mock
    private CustomerFundingEventRepository eventRepository;

    @Mock
    private CustomerFundingAccountRepository fundingAccountRepository;

    @Test
    void matchedProviderEventIsRecordedAsReceivedWithoutCreditingAnything() {
        CustomerFundingAccount account = CustomerFundingAccount.builder()
                .id(UUID.randomUUID())
                .providerCode("PAYSTACK")
                .accountNumber("0123456789")
                .status(CustomerFundingAccountStatus.ACTIVE)
                .build();

        when(eventRepository.findByProviderCodeAndProviderEventId("PAYSTACK", "evt-1"))
                .thenReturn(Optional.empty());
        when(fundingAccountRepository.findByProviderCodeAndAccountNumber("PAYSTACK", "0123456789"))
                .thenReturn(Optional.of(account));
        when(eventRepository.saveAndFlush(any(CustomerFundingEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerFundingEventService service = new CustomerFundingEventService(
                eventRepository,
                fundingAccountRepository
        );

        CustomerFundingEvent event = service.recordReceived(
                new CustomerFundingEventService.ReceivedFundingNotice(
                        "paystack",
                        "evt-1",
                        "provider-txn-1",
                        null,
                        "0123456789",
                        new BigDecimal("1500.00"),
                        "ngn"
                )
        );

        assertEquals(CustomerFundingEventStatus.RECEIVED, event.getStatus());
        assertSame(account, event.getFundingAccount());
        assertEquals(new BigDecimal("1500.00"), event.getAmount());
        assertEquals("NGN", event.getCurrency());
    }

    @Test
    void unmatchedProviderEventIsPreservedForReconciliation() {
        when(eventRepository.findByProviderCodeAndProviderEventId("INTERSWITCH", "evt-unmatched"))
                .thenReturn(Optional.empty());
        when(fundingAccountRepository.findByProviderCodeAndAccountNumber("INTERSWITCH", "9999999999"))
                .thenReturn(Optional.empty());
        when(eventRepository.saveAndFlush(any(CustomerFundingEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerFundingEventService service = new CustomerFundingEventService(
                eventRepository,
                fundingAccountRepository
        );

        CustomerFundingEvent event = service.recordReceived(
                new CustomerFundingEventService.ReceivedFundingNotice(
                        "INTERSWITCH",
                        "evt-unmatched",
                        "provider-txn-2",
                        null,
                        "9999999999",
                        new BigDecimal("500.00"),
                        "NGN"
                )
        );

        assertEquals(CustomerFundingEventStatus.RECONCILIATION_REQUIRED, event.getStatus());
        assertNull(event.getFundingAccount());
    }

    @Test
    void duplicateProviderEventReturnsExistingRecordBeforeAccountResolution() {
        CustomerFundingEvent existing = CustomerFundingEvent.builder()
                .id(UUID.randomUUID())
                .providerCode("PAYSTACK")
                .providerEventId("evt-existing")
                .status(CustomerFundingEventStatus.RECEIVED)
                .amount(new BigDecimal("100.00"))
                .currency("NGN")
                .build();

        when(eventRepository.findByProviderCodeAndProviderEventId("PAYSTACK", "evt-existing"))
                .thenReturn(Optional.of(existing));

        CustomerFundingEventService service = new CustomerFundingEventService(
                eventRepository,
                fundingAccountRepository
        );

        CustomerFundingEvent result = service.recordReceived(
                new CustomerFundingEventService.ReceivedFundingNotice(
                        "PAYSTACK",
                        "evt-existing",
                        "ignored-reference",
                        null,
                        "0123456789",
                        new BigDecimal("100.00"),
                        "NGN"
                )
        );

        assertSame(existing, result);
        verifyNoInteractions(fundingAccountRepository);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void verificationChangesStateOnlyAndDoesNotExposeACreditOperation() {
        UUID eventId = UUID.randomUUID();
        CustomerFundingEvent event = CustomerFundingEvent.builder()
                .id(eventId)
                .providerCode("PAYSTACK")
                .providerEventId("evt-verified")
                .fundingAccount(CustomerFundingAccount.builder().id(UUID.randomUUID()).build())
                .status(CustomerFundingEventStatus.RECEIVED)
                .amount(new BigDecimal("250.00"))
                .currency("NGN")
                .build();

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        CustomerFundingEventService service = new CustomerFundingEventService(
                eventRepository,
                fundingAccountRepository
        );

        CustomerFundingEvent verified = service.markVerified(eventId);

        assertEquals(CustomerFundingEventStatus.VERIFIED, verified.getStatus());
        assertNotNull(verified.getVerifiedAt());
        assertThrows(
                NoSuchMethodException.class,
                () -> CustomerFundingEventService.class.getMethod("credit", UUID.class)
        );
    }
}
