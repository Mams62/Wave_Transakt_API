package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.MerchantProviderEvent;
import com.wavetransakt.merchant.provider.MerchantAcquiringGateway;
import com.wavetransakt.merchant.repository.MerchantProviderEventRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderEventIngressServiceTest {

    @Test
    void verifiedEventIsAcceptedAndOnlyPayloadHashIsPersisted() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        when(gateway.code()).thenReturn("INTERSWITCH");

        byte[] raw = "provider-payload".getBytes(StandardCharsets.UTF_8);
        MerchantAcquiringGateway.ProviderEventVerificationCommand command =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-001", "TERMINAL.LINKED", "sig", "ts", raw
                );

        when(gateway.verifyProviderEvent(command)).thenReturn(
                new MerchantAcquiringGateway.ProviderEventVerificationResult(
                        "INTERSWITCH", true, "evt-001", "TERMINAL.LINKED",
                        "VERIFIED", "signature valid"
                )
        );
        when(repo.findByEventKey("INTERSWITCH:evt-001")).thenReturn(Optional.empty());
        when(repo.save(any(MerchantProviderEvent.class))).thenAnswer(invocation -> {
            MerchantProviderEvent event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("interswitch", command);

        assertEquals("ACCEPTED", result.status());
        assertEquals("VERIFIED", result.verificationStatus());
        assertEquals("VERIFIED_PENDING_HANDLER", result.processingStatus());
        assertFalse(result.duplicate());

        var captor = org.mockito.ArgumentCaptor.forClass(MerchantProviderEvent.class);
        verify(repo).save(captor.capture());
        MerchantProviderEvent stored = captor.getValue();
        assertEquals(64, stored.getPayloadHash().length());
        assertNotEquals(new String(raw, StandardCharsets.UTF_8), stored.getPayloadHash());
        assertNull(stored.getProcessedAt());
    }

    @Test
    void unverifiedEventIsRejectedAndMarkedProcessed() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        when(gateway.code()).thenReturn("INTERSWITCH");

        MerchantAcquiringGateway.ProviderEventVerificationCommand command =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-002", "TERMINAL.LINKED", "bad", "ts", new byte[]{1, 2, 3}
                );
        when(gateway.verifyProviderEvent(command)).thenReturn(
                new MerchantAcquiringGateway.ProviderEventVerificationResult(
                        "INTERSWITCH", false, "evt-002", "TERMINAL.LINKED",
                        "REJECTED", "signature invalid"
                )
        );
        when(repo.findByEventKey("INTERSWITCH:evt-002")).thenReturn(Optional.empty());
        when(repo.save(any(MerchantProviderEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("INTERSWITCH", command);

        assertEquals("REJECTED", result.status());
        assertEquals("REJECTED", result.verificationStatus());
        assertEquals("REJECTED", result.processingStatus());

        var captor = org.mockito.ArgumentCaptor.forClass(MerchantProviderEvent.class);
        verify(repo).save(captor.capture());
        assertNotNull(captor.getValue().getProcessedAt());
    }

    @Test
    void duplicateEventDoesNotInvokeProviderVerificationAgain() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        when(gateway.code()).thenReturn("INTERSWITCH");

        MerchantProviderEvent existing = MerchantProviderEvent.builder()
                .id(UUID.randomUUID())
                .providerCode("INTERSWITCH")
                .eventKey("INTERSWITCH:evt-003")
                .eventType("TERMINAL.LINKED")
                .payloadHash("a".repeat(64))
                .verificationStatus("VERIFIED")
                .processingStatus("VERIFIED_PENDING_HANDLER")
                .build();
        when(repo.findByEventKey("INTERSWITCH:evt-003")).thenReturn(Optional.of(existing));

        MerchantAcquiringGateway.ProviderEventVerificationCommand command =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-003", "TERMINAL.LINKED", "sig", "ts", new byte[]{9}
                );

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("INTERSWITCH", command);

        assertTrue(result.duplicate());
        assertEquals("DUPLICATE", result.status());
        verify(gateway, never()).verifyProviderEvent(any());
        verify(repo, never()).save(any());
    }

    @Test
    void mismatchedVerifiedIdentityIsRejected() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        when(gateway.code()).thenReturn("INTERSWITCH");

        MerchantAcquiringGateway.ProviderEventVerificationCommand command =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-004", "TERMINAL.LINKED", "sig", "ts", new byte[]{4}
                );
        when(gateway.verifyProviderEvent(command)).thenReturn(
                new MerchantAcquiringGateway.ProviderEventVerificationResult(
                        "INTERSWITCH", true, "different-event", "TERMINAL.LINKED",
                        "VERIFIED", "signature valid"
                )
        );
        when(repo.findByEventKey("INTERSWITCH:evt-004")).thenReturn(Optional.empty());
        when(repo.save(any(MerchantProviderEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("INTERSWITCH", command);

        assertEquals("REJECTED", result.status());
        assertEquals("REJECTED", result.verificationStatus());
    }
}
