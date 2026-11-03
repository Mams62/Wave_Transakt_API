-- SIMULATION-ONLY. Does not migrate wallets, user balances or real transfers.
CREATE TABLE partner_sandbox_intents (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id),
    reference VARCHAR(70) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    amount_kobo BIGINT NOT NULL CHECK (amount_kobo BETWEEN 1 AND 500000000),
    merchant_reference VARCHAR(70) NOT NULL,
    state VARCHAR(25) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_partner_sim_actor_key UNIQUE (actor_id, idempotency_key),
    CONSTRAINT chk_partner_sim_state CHECK
        (state IN ('CREATED','PENDING','TEST_CONFIRMED','TEST_FAILED','REVIEW_REQUIRED'))
);
CREATE INDEX ix_partner_sim_created ON partner_sandbox_intents(created_at);

CREATE TABLE partner_sandbox_events (
    id UUID PRIMARY KEY,
    event_key VARCHAR(96) NOT NULL UNIQUE,
    intent_reference VARCHAR(70),
    payload_hash VARCHAR(64) NOT NULL,
    claimed_state VARCHAR(20) NOT NULL,
    reconciliation_outcome VARCHAR(40) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_partner_sim_event_ref ON partner_sandbox_events(intent_reference);
-- Deliberately NO automatic ledger/funding/settlement triggers.
