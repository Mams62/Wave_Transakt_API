package com.wavetransakt.partner.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavetransakt.transaction.exception.IdempotencyConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PartnerSandboxServiceTest {
    private PartnerSandboxIntentRepository intents;
    private PartnerSandboxEventRepository events;
    private PartnerSandboxSettings settings;
    private PartnerSandboxService service;
    private static final String SECRET = "simulation-only-secret-123456789012345678901";

    @BeforeEach
    void setup() {
        intents = mock(PartnerSandboxIntentRepository.class);
        events = mock(PartnerSandboxEventRepository.class);
        settings = mock(PartnerSandboxSettings.class);
        service = new PartnerSandboxService(intents, events, settings, new ObjectMapper());
    }

    @Test void duplicateIdempotencyKeyReturnsSameTestIntent() {
        UUID actor = UUID.randomUUID();
        String key = "request_123456789012345";
        String hash = SandboxWebhookSignature.sha256Hex(
                (actor + "\n" + 12500L + "\n" + "ORDER-XYZ").getBytes(StandardCharsets.UTF_8));
        PartnerSandboxIntent persisted = mock(PartnerSandboxIntent.class);
        when(persisted.getRequestHash()).thenReturn(hash);
        when(persisted.getReference()).thenReturn("SIM-" + UUID.randomUUID());
        when(persisted.getAmountKobo()).thenReturn(12500L);
        when(persisted.getMerchantReference()).thenReturn("ORDER-XYZ");
        when(persisted.getState()).thenReturn(PartnerSandboxState.CREATED);
        when(persisted.getCreatedAt()).thenReturn(Instant.now());
        when(intents.findByActorIdAndIdempotencyKey(actor, key)).thenReturn(Optional.of(persisted));
        when(intents.reserve(any(UUID.class), eq(actor), anyString(), eq(key), eq(hash), eq(12500L), eq("ORDER-XYZ")))
                .thenReturn(1, 0);
        var first = service.create(actor, key, new PartnerSandboxService.IntentRequest(12500L, "ORDER-XYZ"));
        var second = service.create(actor, key, new PartnerSandboxService.IntentRequest(12500L, "ORDER-XYZ"));
        assertEquals(first.reference(), second.reference());
        assertTrue(first.simulationOnly());
        assertFalse(first.realMoneyMoved());
        verify(intents, times(2)).reserve(any(UUID.class), eq(actor), anyString(), eq(key),
                eq(hash), eq(12500L), eq("ORDER-XYZ"));
    }

    @Test void alteredPayloadWithSameIdempotencyKeyIsRejected() {
        UUID actor = UUID.randomUUID();
        String key = "request_123456789012345";
        PartnerSandboxIntent original = mock(PartnerSandboxIntent.class);
        when(original.getRequestHash()).thenReturn("different-content-hash");
        when(intents.findByActorIdAndIdempotencyKey(actor, key)).thenReturn(Optional.of(original));
        assertThrows(IdempotencyConflictException.class, () ->
                service.create(actor, key, new PartnerSandboxService.IntentRequest(12500L, "ORDER-XYZ")));
    }

    @Test void badIdempotencyKeysAndAmountsFailBeforeWrite() {
        UUID actor = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                service.create(actor, "short", new PartnerSandboxService.IntentRequest(100L, "ORDER-1")));
        assertThrows(IllegalArgumentException.class, () ->
                service.create(actor, "request_123456789012345", new PartnerSandboxService.IntentRequest(-1L, "ORDER-1")));
        verifyNoInteractions(intents);
    }

    @Test void invalidWebhookSignatureNeverTouchesDatabase() {
        when(settings.simulationSecret()).thenReturn(SECRET);
        assertThrows(ResponseStatusException.class, () ->
                service.receiveSimulationCallback("event_123456789", "1800000000", "00", "{}".getBytes(StandardCharsets.UTF_8)));
        verifyNoInteractions(events, intents);
    }

    @Test void authenticTestCallbackMayMarkPendingButNeverCreditLedger() throws Exception {
        when(settings.simulationSecret()).thenReturn(SECRET);
        String ref = "SIM-" + UUID.randomUUID();
        String eventId = "sim_event_1234567890";
        byte[] bytes = new ObjectMapper().writeValueAsBytes(
                new PartnerSandboxService.CallbackPayload(ref, 12000L, "NGN", "PENDING"));
        String ts = Long.toString(Instant.now().getEpochSecond());
        String sig = HexFormat.of().formatHex(SandboxWebhookSignature.sign(SECRET, ts, bytes));
        PartnerSandboxIntent intent = mock(PartnerSandboxIntent.class);
        when(intent.getAmountKobo()).thenReturn(12000L);
        when(intent.getState()).thenReturn(PartnerSandboxState.CREATED);
        when(intents.findByReferenceForUpdate(ref)).thenReturn(Optional.of(intent));
        when(events.reserve(any(UUID.class), eq(eventId), eq(ref), anyString(), eq("PENDING"))).thenReturn(1);
        PartnerSandboxEvent stored = mock(PartnerSandboxEvent.class);
        when(stored.getPayloadHash()).thenReturn(SandboxWebhookSignature.sha256Hex(bytes));
        when(events.findByEventKey(eventId)).thenReturn(Optional.of(stored));
        var result = service.receiveSimulationCallback(eventId, ts, sig, bytes);
        assertEquals("APPLIED_TEST_ONLY", result.outcome());
        assertEquals("SIMULATION_ONLY_NO_MONEY", result.mode());
        assertFalse(result.duplicate());
        verify(intent).setState(PartnerSandboxState.PENDING);
        verify(intents).save(intent);
        verify(events).save(stored);
    }

    @Test void repeatedSignedCallbackIsDeduplicatedBeforeMoneyStateUpdates() throws Exception {
        when(settings.simulationSecret()).thenReturn(SECRET);
        String ref = "SIM-" + UUID.randomUUID();
        String eventId = "sim_event_1234567890";
        byte[] bytes = new ObjectMapper().writeValueAsBytes(
                new PartnerSandboxService.CallbackPayload(ref, 12000L, "NGN", "CONFIRMED"));
        String ts = Long.toString(Instant.now().getEpochSecond());
        String sig = HexFormat.of().formatHex(SandboxWebhookSignature.sign(SECRET, ts, bytes));
        when(events.reserve(any(UUID.class), eq(eventId), eq(ref), anyString(), eq("CONFIRMED"))).thenReturn(0);
        PartnerSandboxEvent event = mock(PartnerSandboxEvent.class);
        when(event.getPayloadHash()).thenReturn(SandboxWebhookSignature.sha256Hex(bytes));
        when(event.getReconciliationOutcome()).thenReturn("APPLIED_TEST_ONLY");
        when(events.findByEventKey(eventId)).thenReturn(Optional.of(event));
        var response = service.receiveSimulationCallback(eventId, ts, sig, bytes);
        assertTrue(response.duplicate());
        verifyNoInteractions(intents);
    }

    @Test void reconciliationIsAlwaysReadOnlyAndExplicitlyNonFinancial() {
        when(intents.findByStateInOrderByCreatedAtAsc(anyList(), any())).thenReturn(java.util.List.of());
        var result = service.reconciliation();
        assertEquals("SIMULATION_ONLY_NO_FUNDS", result.mode());
        assertFalse(result.ledgerCredited());
        verify(intents, never()).save(any());
        verify(events, never()).save(any());
    }
}
