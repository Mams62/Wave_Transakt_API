package com.wavetransakt.partner.sandbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Verified, replay-protected simulation callback. No PII/raw payload stored. */
@Entity
@Table(name = "partner_sandbox_events", indexes =
        @Index(name = "ix_partner_sim_event_ref", columnList = "intent_reference"))
public class PartnerSandboxEvent {
    @Id
    private UUID id;
    @Column(name = "event_key", nullable = false, unique = true, length = 96)
    private String eventKey;
    @Column(name = "intent_reference", length = 70)
    private String intentReference;
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
    @Column(name = "claimed_state", nullable = false, length = 20)
    private String claimedState;
    @Column(name = "reconciliation_outcome", nullable = false, length = 40)
    private String reconciliationOutcome;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    public UUID getId() { return id; }
    public String getEventKey() { return eventKey; }
    public String getIntentReference() { return intentReference; }
    public String getPayloadHash() { return payloadHash; }
    public String getClaimedState() { return claimedState; }
    public String getReconciliationOutcome() { return reconciliationOutcome; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReconciliationOutcome(String outcome) { reconciliationOutcome = outcome; }
}
