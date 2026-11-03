package com.wavetransakt.partner.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavetransakt.transaction.exception.IdempotencyConflictException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * SIMULATED test instructions only. Does not invoke Wema, create wallets,
 * credit a ledger, debit anyone, or claim bank-verified settlement.
 */
@Service
public class PartnerSandboxService {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9._:-]{3,70}");
    private static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9._:-]{8,96}");
    private static final long MAX_KOBO = 500_000_000L;

    private final PartnerSandboxIntentRepository intents;
    private final PartnerSandboxEventRepository events;
    private final PartnerSandboxSettings settings;
    private final ObjectMapper mapper;

    public PartnerSandboxService(
            PartnerSandboxIntentRepository intents,
            PartnerSandboxEventRepository events,
            PartnerSandboxSettings settings,
            ObjectMapper mapper
    ) {
        this.intents = intents;
        this.events = events;
        this.settings = settings;
        this.mapper = mapper;
    }

    /** Credentials are never sent to the client. 'Configured' != bank-approved. */
    public Readiness readiness() {
        return new Readiness(
                "WEMA",
                "AWAITING_OFFICIAL_BANK_SANDBOX_CONTRACT",
                settings.simulationEnabled(),
                settings.webhookVerificationReady(),
                settings.credentialsPresent(),
                settings.bankApiContractVerified(),
                settings.realMoneyMovementEnabled(),
                "All simulated statuses are TEST ONLY; transfers, settlement and wallet credit are disabled."
        );
    }

    @Transactional
    public IntentView create(UUID actorId, String idempotencyKey, IntentRequest request) {
        settings.requireSimulation();
        if (actorId == null || request == null) {
            throw new IllegalArgumentException("Authenticated customer and intent are required");
        }
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must be 16-128 safe characters");
        }
        if (request.amountKobo() == null || request.amountKobo() < 1 || request.amountKobo() > MAX_KOBO) {
            throw new IllegalArgumentException("Sandbox amount is out of range");
        }
        String merchantRef = request.merchantReference() == null ? "" : request.merchantReference().trim();
        if (!REF.matcher(merchantRef).matches()) {
            throw new IllegalArgumentException("Merchant reference must be 3-70 safe characters");
        }
        // Stable canonical fingerprint. Prevents key reuse with altered payment data.
        byte[] canonical = (actorId + "\n" + request.amountKobo() + "\n" + merchantRef)
                .getBytes(StandardCharsets.UTF_8);
        String hash = SandboxWebhookSignature.sha256Hex(canonical);
        String reference = "SIM-" + UUID.randomUUID();
        intents.reserve(UUID.randomUUID(), actorId, reference, idempotencyKey,
                hash, request.amountKobo(), merchantRef);
        PartnerSandboxIntent stored = intents.findByActorIdAndIdempotencyKey(actorId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Unable to read sandbox reservation"));
        if (!stored.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException("Idempotency-Key was already used for a different instruction");
        }
        return toView(stored);
    }

    @Transactional(readOnly = true)
    public IntentView owned(UUID actorId, String reference) {
        if (actorId == null || reference == null || reference.length() > 70) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sandbox instruction not found");
        }
        PartnerSandboxIntent intent = intents.findByActorIdAndReference(actorId, reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sandbox instruction not found"));
        return toView(intent);
    }

    /**
     * This callback has a Wave-owned SIMULATION HMAC scheme. Wema's actual
     * signature scheme is UNKNOWN; this endpoint must never be given to Wema
     * or enabled as a production provider callback.
     */
    @Transactional
    public CallbackReceipt receiveSimulationCallback(
            String eventId, String timestamp, String signatureHex, byte[] body
    ) {
        settings.requireSimulation();
        String secret = settings.simulationSecret();
        SandboxWebhookSignature.verify(secret, timestamp, signatureHex, body, Instant.now());
        if (eventId == null || !EVENT_ID.matcher(eventId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation event ID");
        }
        CallbackPayload payload;
        try {
            payload = mapper.readValue(body, CallbackPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation payload");
        }
        if (payload.reference() == null || !payload.reference().matches("SIM-[a-fA-F0-9-]{36}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation reference");
        }
        if (!"NGN".equals(payload.currency()) || payload.amountKobo() == null ||
                payload.amountKobo() < 1 || payload.amountKobo() > MAX_KOBO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation amount or currency");
        }
        // Strict parser; never accept an unknown event as a success.
        PartnerSandboxState target = PartnerSandboxState.CREATED.next(payload.status());
        String hash = SandboxWebhookSignature.sha256Hex(body);
        int inserted = events.reserve(UUID.randomUUID(), eventId, payload.reference(), hash,
                payload.status());
        PartnerSandboxEvent recorded = events.findByEventKey(eventId)
                .orElseThrow(() -> new IllegalStateException("Sandbox event reservation missing"));
        if (!recorded.getPayloadHash().equals(hash)) {
            throw new IdempotencyConflictException("A simulation event ID was reused with a different payload");
        }
        if (inserted == 0) {
            return new CallbackReceipt(true, recorded.getReconciliationOutcome(), "SIMULATION_ONLY_NO_MONEY");
        }
        PartnerSandboxIntent instruction = intents.findByReferenceForUpdate(payload.reference()).orElse(null);
        String outcome;
        if (instruction == null) {
            outcome = "UNMATCHED_REFERENCE";
        } else if (instruction.getAmountKobo() != payload.amountKobo()) {
            outcome = "AMOUNT_MISMATCH";
        } else {
            PartnerSandboxState next = instruction.getState().next(payload.status());
            instruction.setState(next);
            intents.save(instruction);
            outcome = next == PartnerSandboxState.REVIEW_REQUIRED
                    ? "REVIEW_REQUIRED" : "APPLIED_TEST_ONLY";
        }
        recorded.setReconciliationOutcome(outcome);
        events.save(recorded);
        return new CallbackReceipt(false, outcome, "SIMULATION_ONLY_NO_MONEY");
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconciliation() {
        List<PartnerSandboxIntent> unresolved = intents.findByStateInOrderByCreatedAtAsc(
                List.of(PartnerSandboxState.CREATED, PartnerSandboxState.PENDING,
                        PartnerSandboxState.REVIEW_REQUIRED), PageRequest.of(0, 100));
        List<ReconciliationItem> items = unresolved.stream()
                .map(i -> new ReconciliationItem(i.getReference(), i.getState().name(),
                        i.getAmountKobo(), i.getCreatedAt()))
                .toList();
        return new ReconciliationReport(
                "SIMULATION_ONLY_NO_FUNDS",
                intents.countByState(PartnerSandboxState.CREATED),
                intents.countByState(PartnerSandboxState.PENDING),
                intents.countByState(PartnerSandboxState.TEST_CONFIRMED),
                intents.countByState(PartnerSandboxState.TEST_FAILED),
                intents.countByState(PartnerSandboxState.REVIEW_REQUIRED),
                events.countByReconciliationOutcome("AMOUNT_MISMATCH"),
                events.countByReconciliationOutcome("UNMATCHED_REFERENCE"),
                items,
                false
        );
    }

    private IntentView toView(PartnerSandboxIntent intent) {
        return new IntentView(intent.getReference(), intent.getState().name(),
                intent.getAmountKobo(), "NGN", intent.getMerchantReference(),
                intent.getCreatedAt(), false, true);
    }

    public record IntentRequest(Long amountKobo, String merchantReference) {}
    public record CallbackPayload(String reference, Long amountKobo, String currency, String status) {}
    public record Readiness(
            String provider, String bankIntegrationStatus,
            boolean sandboxSimulationEnabled, boolean webhookSimulationReady,
            boolean bankCredentialFieldsPresent, boolean bankContractVerified,
            boolean paymentExecutionEnabled, String message
    ) {}
    public record IntentView(
            String reference, String status, long amountKobo, String currency,
            String merchantReference, Instant createdAt,
            boolean realMoneyMoved, boolean simulationOnly
    ) {}
    public record CallbackReceipt(boolean duplicate, String outcome, String mode) {}
    public record ReconciliationItem(String reference, String status, long amountKobo, Instant createdAt) {}
    public record ReconciliationReport(
            String mode, long created, long pending, long testConfirmed, long testFailed,
            long reviewRequired, long amountMismatchEvents, long unmatchedEvents,
            List<ReconciliationItem> unresolvedFirst100, boolean ledgerCredited
    ) {}
}
