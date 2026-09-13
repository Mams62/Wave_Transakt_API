CREATE TABLE merchant_provider_events (
    id UUID PRIMARY KEY,
    provider_code VARCHAR(40) NOT NULL,
    event_key VARCHAR(180) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    provider_reference VARCHAR(160),
    payment_reference VARCHAR(80),
    payload_hash VARCHAR(64) NOT NULL,
    verification_status VARCHAR(40) NOT NULL,
    processing_status VARCHAR(40) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT ux_merchant_provider_events_event_key UNIQUE (event_key)
);

CREATE INDEX idx_merchant_provider_events_payment_reference ON merchant_provider_events(payment_reference);
CREATE INDEX idx_merchant_provider_events_provider_reference ON merchant_provider_events(provider_reference);

CREATE TABLE merchant_settlement_batches (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    provider_code VARCHAR(40),
    provider_batch_reference VARCHAR(160),
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    gross_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    fee_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    payment_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    settlement_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_merchant_settlement_batches_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT ux_merchant_settlement_batches_provider_ref UNIQUE (provider_code, provider_batch_reference)
);

CREATE INDEX idx_merchant_settlement_batches_merchant ON merchant_settlement_batches(merchant_id, created_at DESC);

CREATE TABLE merchant_reconciliation_items (
    id UUID PRIMARY KEY,
    settlement_batch_id UUID,
    merchant_payment_id UUID NOT NULL,
    provider_reference VARCHAR(160),
    expected_amount NUMERIC(19,2) NOT NULL,
    provider_amount NUMERIC(19,2),
    difference_amount NUMERIC(19,2),
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_merchant_reconciliation_batch FOREIGN KEY (settlement_batch_id) REFERENCES merchant_settlement_batches(id),
    CONSTRAINT fk_merchant_reconciliation_payment FOREIGN KEY (merchant_payment_id) REFERENCES merchant_payments(id),
    CONSTRAINT ux_merchant_reconciliation_payment UNIQUE (merchant_payment_id)
);

CREATE INDEX idx_merchant_reconciliation_batch ON merchant_reconciliation_items(settlement_batch_id);
