ALTER TABLE customer_funding_events
    ADD COLUMN credit_policy_code VARCHAR(80),
    ADD COLUMN credit_decision VARCHAR(40),
    ADD COLUMN credit_decision_reason VARCHAR(255),
    ADD COLUMN credit_decided_at TIMESTAMP,
    ADD COLUMN ledger_reference VARCHAR(100);

CREATE UNIQUE INDEX uq_customer_funding_event_ledger_reference
    ON customer_funding_events(ledger_reference)
    WHERE ledger_reference IS NOT NULL;

CREATE INDEX idx_customer_funding_event_credit_decision
    ON customer_funding_events(credit_decision);

-- Intentionally do not seed provider settlement asset accounts here.
-- A provider-specific approval/migration must create
-- SYSTEM:PROVIDER_SETTLEMENT:<PROVIDER>:<CURRENCY> before crediting can occur.
