package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.MerchantProviderEvent;
import com.wavetransakt.merchant.provider.MerchantAcquiringGateway;
import com.wavetransakt.merchant.repository.MerchantProviderEventRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderEventIngressServiceTest {

    @Test
    void verifiedEventIsAcceptedAndAtomicallyReservedByTrustedEventId() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        Map<String, MerchantProviderEvent> store = wireAtomicStore(repo);
        when(gateway.code()).thenReturn("INTERSWITCH");

        byte[] raw = "provider-payload".getBytes(StandardCharsets.UTF_8);
        MerchantAcquiringGateway.ProviderEventVerificationCommand command =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-001", "TERMINAL.LINKED", "sig", "ts", raw
                );

        when(gateway.verifyProviderEvent(command)).thenReturn(
                verified("evt-001", "TERMINAL.LINKED")
        );

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("interswitch", command);

        assertEquals("ACCEPTED", result.status());
        assertEquals("VERIFIED", result.verificationStatus());
        assertEquals("VERIFIED_PENDING_HANDLER", result.processingStatus());
        assertFalse(result.duplicate());

        MerchantProviderEvent stored = store.get("INTERSWITCH:evt-001");
        assertNotNull(stored);
        assertEquals(64, stored.getPayloadHash().length());
        assertNotEquals(new String(raw, StandardCharsets.UTF_8), stored.getPayloadHash());
        assertNull(stored.getProcessedAt());
        verify(gateway).verifyProviderEvent(command);
        verify(repo, never()).save(any());
    }

    @Test
    void unverifiedEventUsesSeparateHashedAuditKeyAndCannotClaimTrustedReplayKey() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        Map<String, MerchantProviderEvent> store = wireAtomicStore(repo);
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

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("INTERSWITCH", command);

        assertEquals("REJECTED", result.status());
        assertEquals("REJECTED", result.verificationStatus());
        assertEquals("REJECTED", result.processingStatus());
        assertFalse(store.containsKey("INTERSWITCH:evt-002"));

        MerchantProviderEvent rejected = store.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("INTERSWITCH:REJECTED:"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        assertNotNull(rejected.getProcessedAt());
        assertEquals("evt-002", rejected.getProviderReference());
    }

    @Test
    void duplicateVerifiedEventIsReverifiedThenReturnsExistingReservation() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        wireAtomicStore(repo);
        when(gateway.code()).thenReturn("INTERSWITCH");

        MerchantAcquiringGateway.ProviderEventVerificationCommand command =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-003", "TERMINAL.LINKED", "sig", "ts", new byte[]{9}
                );
        when(gateway.verifyProviderEvent(command)).thenReturn(
                verified("evt-003", "TERMINAL.LINKED")
        );

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult first = service.ingest("INTERSWITCH", command);
        ProviderEventIngressService.IngressResult second = service.ingest("INTERSWITCH", command);

        assertFalse(first.duplicate());
        assertTrue(second.duplicate());
        assertEquals("DUPLICATE", second.status());
        assertEquals(first.eventRecordId(), second.eventRecordId());

        /*
         * Even duplicate claims are authenticated before the event ID is trusted.
         * This prevents unauthenticated callers from probing/claiming event IDs.
         */
        verify(gateway, times(2)).verifyProviderEvent(command);
        verify(repo, times(2)).insertIfAbsent(
                eq("INTERSWITCH"),
                eq("INTERSWITCH:evt-003"),
                eq("TERMINAL.LINKED"),
                eq("evt-003"),
                anyString(),
                eq("VERIFIED"),
                eq("VERIFIED_PENDING_HANDLER"),
                isNull()
        );
    }

    @Test
    void forgedRejectedAttemptCannotPoisonLaterGenuineEventWithSameId() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        Map<String, MerchantProviderEvent> store = wireAtomicStore(repo);
        when(gateway.code()).thenReturn("INTERSWITCH");

        MerchantAcquiringGateway.ProviderEventVerificationCommand forged =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-real-001", "PAYMENT.SETTLED", "forged", "ts-1", new byte[]{4}
                );
        MerchantAcquiringGateway.ProviderEventVerificationCommand genuine =
                new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                        "evt-real-001", "PAYMENT.SETTLED", "valid", "ts-2", new byte[]{5}
                );

        when(gateway.verifyProviderEvent(forged)).thenReturn(
                new MerchantAcquiringGateway.ProviderEventVerificationResult(
                        "INTERSWITCH", false, "evt-real-001", "PAYMENT.SETTLED",
                        "REJECTED", "invalid signature"
                )
        );
        when(gateway.verifyProviderEvent(genuine)).thenReturn(
                verified("evt-real-001", "PAYMENT.SETTLED")
        );

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult rejected = service.ingest("INTERSWITCH", forged);
        ProviderEventIngressService.IngressResult accepted = service.ingest("INTERSWITCH", genuine);

        assertEquals("REJECTED", rejected.status());
        assertEquals("ACCEPTED", accepted.status());
        assertFalse(accepted.duplicate());
        assertTrue(store.containsKey("INTERSWITCH:evt-real-001"));
        assertTrue(store.keySet().stream().anyMatch(key -> key.startsWith("INTERSWITCH:REJECTED:")));
    }

    @Test
    void mismatchedVerifiedIdentityIsRejectedWithoutOccupyingTrustedEventKey() {
        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        MerchantProviderEventRepository repo = mock(MerchantProviderEventRepository.class);
        Map<String, MerchantProviderEvent> store = wireAtomicStore(repo);
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

        ProviderEventIngressService service = new ProviderEventIngressService(List.of(gateway), repo);
        ProviderEventIngressService.IngressResult result = service.ingest("INTERSWITCH", command);

        assertEquals("REJECTED", result.status());
        assertEquals("REJECTED", result.verificationStatus());
        assertFalse(store.containsKey("INTERSWITCH:evt-004"));
    }

    private MerchantAcquiringGateway.ProviderEventVerificationResult verified(
            String eventId,
            String eventType
    ) {
        return new MerchantAcquiringGateway.ProviderEventVerificationResult(
                "INTERSWITCH",
                true,
                eventId,
                eventType,
                "VERIFIED",
                "signature valid"
        );
    }

    private Map<String, MerchantProviderEvent> wireAtomicStore(
            MerchantProviderEventRepository repo
    ) {
        Map<String, MerchantProviderEvent> store = new HashMap<>();

        when(repo.insertIfAbsent(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            String eventKey = invocation.getArgument(1);
            if (store.containsKey(eventKey)) {
                return 0;
            }

            MerchantProviderEvent event = MerchantProviderEvent.builder()
                    .id(UUID.randomUUID())
                    .providerCode(invocation.getArgument(0))
                    .eventKey(eventKey)
                    .eventType(invocation.getArgument(2))
                    .providerReference(invocation.getArgument(3))
                    .payloadHash(invocation.getArgument(4))
                    .verificationStatus(invocation.getArgument(5))
                    .processingStatus(invocation.getArgument(6))
                    .receivedAt(LocalDateTime.now())
                    .processedAt(invocation.getArgument(7))
                    .build();
            store.put(eventKey, event);
            return 1;
        });

        when(repo.findByEventKey(anyString())).thenAnswer(
                invocation -> Optional.ofNullable(store.get(invocation.getArgument(0)))
        );

        return store;
    }
}
