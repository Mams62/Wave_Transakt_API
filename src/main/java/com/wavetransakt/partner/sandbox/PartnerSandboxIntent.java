package com.wavetransakt.partner.sandbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Persistent test instruction; cannot debit a wallet or move real money. */
@Entity
@Table(name = "partner_sandbox_intents",
        uniqueConstraints = @UniqueConstraint(name = "ux_partner_sim_actor_key", columnNames = {"actor_id", "idempotency_key"}),
        indexes = @Index(name = "ix_partner_sim_created", columnList = "created_at"))
public class PartnerSandboxIntent {
    @Id
    private UUID id;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(nullable = false, unique = true, length = 70)
    private String reference;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "amount_kobo", nullable = false)
    private long amountKobo;
    @Column(name = "merchant_reference", nullable = false, length = 70)
    private String merchantReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private PartnerSandboxState state = PartnerSandboxState.CREATED;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getReference() { return reference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public long getAmountKobo() { return amountKobo; }
    public String getMerchantReference() { return merchantReference; }
    public PartnerSandboxState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setState(PartnerSandboxState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }
}
