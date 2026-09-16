CREATE TABLE customer_funding_reconciliation_actions (
    id UUID PRIMARY KEY,
    funding_event_id UUID NOT NULL REFERENCES customer_funding_events(id),
    action_type VARCHAR(50) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_funding_reconciliation_action_event_created
    ON customer_funding_reconciliation_actions(funding_event_id, created_at);

CREATE INDEX idx_funding_reconciliation_action_actor
    ON customer_funding_reconciliation_actions(actor_user_id, created_at);
