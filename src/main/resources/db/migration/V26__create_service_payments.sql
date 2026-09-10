CREATE TABLE service_payments (
    id UUID PRIMARY KEY,
    reference VARCHAR(60) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    wallet_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'VTPASS',
    service_kind VARCHAR(20) NOT NULL,
    service_id VARCHAR(80) NOT NULL,
    service_name VARCHAR(140) NOT NULL,
    variation_code VARCHAR(120),
    recipient VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    provider_request_id VARCHAR(100) NOT NULL UNIQUE,
    provider_transaction_id VARCHAR(120),
    provider_status VARCHAR(80),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider_message VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_service_payment_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets(id),

    CONSTRAINT ux_service_payment_wallet_idempotency
        UNIQUE (wallet_id, idempotency_key),

    CONSTRAINT chk_service_payment_amount
        CHECK (amount > 0),

    CONSTRAINT chk_service_payment_kind
        CHECK (service_kind IN ('AIRTIME', 'DATA')),

    CONSTRAINT chk_service_payment_status
        CHECK (status IN ('PENDING', 'SUCCESSFUL', 'FAILED'))
);

CREATE INDEX idx_service_payment_reference
    ON service_payments(reference);

CREATE INDEX idx_service_payment_wallet
    ON service_payments(wallet_id);

CREATE INDEX idx_service_payment_provider_request
    ON service_payments(provider_request_id);

CREATE INDEX idx_service_payment_created
    ON service_payments(created_at);

-- Provider settlement/clearing account used when a service payment is reserved.
-- A pending provider outcome keeps the wallet amount reserved until requery
-- resolves the payment. A confirmed failure creates a reversal journal.
INSERT INTO ledger_accounts (
    id,
    code,
    account_type,
    account_class,
    wallet_id,
    currency
)
VALUES (
    gen_random_uuid(),
    'SYSTEM:VTPASS_SETTLEMENT:NGN',
    'SYSTEM',
    'ASSET',
    NULL,
    'NGN'
)
ON CONFLICT (code) DO NOTHING;
