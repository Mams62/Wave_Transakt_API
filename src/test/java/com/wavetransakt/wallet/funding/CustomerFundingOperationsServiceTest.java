package com.wavetransakt.wallet.funding;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerFundingOperationsServiceTest {

    @Mock CustomerFundingEventRepository eventRepository;
    @Mock CustomerFundingReconciliationActionRepository actionRepository;

    @Test
    void nonAdminCannotReadOperationsQueue() {
        CustomerFundingOperationsService service = service();
        User user = User.builder().id(UUID.randomUUID()).role(UserRole.USER).build();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listEvents(null, null, null, null, user)
        );

        verifyNoInteractions(eventRepository, actionRepository);
    }

    @Test
    void queueDefaultsToReconciliationRequiredAndExposesNoCustomerIdentity() {
        User admin = admin();
        CustomerFundingEvent event = CustomerFundingEvent.builder()
                .id(UUID.randomUUID())
                .providerCode("TESTPROVIDER")
                .providerEventId("evt-123")
                .providerReference("provider-ref-123")
                .status(CustomerFundingEventStatus.RECONCILIATION_REQUIRED)
                .amount(new BigDecimal("125.00"))
                .currency("NGN")
                .build();

        when(eventRepository.findByStatus(
                eq(CustomerFundingEventStatus.RECONCILIATION_REQUIRED),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(event)));

        CustomerFundingOperationsService.FundingEventPage result = service()
                .listEvents(null, null, null, null, admin);

        assertEquals(1, result.content().size());
        CustomerFundingOperationsService.FundingEventSummary summary = result.content().getFirst();
        assertEquals(event.getId(), summary.eventId());
        assertEquals("TESTPROVIDER", summary.providerCode());
        assertEquals(CustomerFundingEventStatus.RECONCILIATION_REQUIRED, summary.status());
        assertNull(summary.fundingAccountId());
        assertNull(summary.walletId());
    }

    @Test
    void operatorActionIsAppendOnlyAndDoesNotMutateFundingEvent() {
        UUID eventId = UUID.randomUUID();
        User admin = admin();
        CustomerFundingEvent event = CustomerFundingEvent.builder()
                .id(eventId)
                .providerCode("TESTPROVIDER")
                .providerEventId("evt-456")
                .status(CustomerFundingEventStatus.RECONCILIATION_REQUIRED)
                .amount(new BigDecimal("50.00"))
                .currency("NGN")
                .build();

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(actionRepository.save(any(CustomerFundingReconciliationAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerFundingOperationsService.ReconciliationActionView result = service().recordAction(
                eventId,
                FundingReconciliationActionType.ESCALATED,
                FundingReconciliationReasonCode.PROVIDER_STATUS_UNRESOLVED,
                admin
        );

        assertEquals(CustomerFundingEventStatus.RECONCILIATION_REQUIRED, event.getStatus());
        assertEquals(FundingReconciliationActionType.ESCALATED, result.actionType());
        assertEquals(FundingReconciliationReasonCode.PROVIDER_STATUS_UNRESOLVED, result.reasonCode());
        assertEquals(admin.getId(), result.actorUserId());

        ArgumentCaptor<CustomerFundingReconciliationAction> captor =
                ArgumentCaptor.forClass(CustomerFundingReconciliationAction.class);
        verify(actionRepository).save(captor.capture());
        assertEquals(eventId, captor.getValue().getFundingEventId());
        assertEquals(admin.getId(), captor.getValue().getActorUserId());
        verify(eventRepository, never()).save(any(CustomerFundingEvent.class));
    }

    @Test
    void operatorCannotAttachReconciliationActionToCreditedEvent() {
        UUID eventId = UUID.randomUUID();
        CustomerFundingEvent event = CustomerFundingEvent.builder()
                .id(eventId)
                .providerCode("TESTPROVIDER")
                .providerEventId("evt-terminal")
                .status(CustomerFundingEventStatus.CREDITED)
                .amount(new BigDecimal("50.00"))
                .currency("NGN")
                .build();

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        assertThrows(
                IllegalStateException.class,
                () -> service().recordAction(
                        eventId,
                        FundingReconciliationActionType.ACKNOWLEDGED,
                        FundingReconciliationReasonCode.MANUAL_REVIEW_REQUIRED,
                        admin()
                )
        );

        verifyNoInteractions(actionRepository);
        verify(eventRepository, never()).save(any(CustomerFundingEvent.class));
    }

    private CustomerFundingOperationsService service() {
        return new CustomerFundingOperationsService(eventRepository, actionRepository);
    }

    private User admin() {
        return User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.ADMIN)
                .build();
    }
}
