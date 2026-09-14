CREATE TABLE external_bank_transfers (
    id UUID PRIMARY KEY,
    reference VARCHAR(70) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    wallet_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'INTERSWITCH',
    destination_bank_code VARCHAR(20) NOT NULL,
    destination_account_number VARCHAR(10) NOT NULL,
    destination_account_name VARCHAR(160) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    fee NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    narration VARCHAR(160),
    provider_transfer_code VARCHAR(80),
    provider_reference VARCHAR(120),
    provider_response_code VARCHAR(40),
    provider_message VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_external_bank_transfer_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets(id),

    CONSTRAINT ux_external_bank_transfer_wallet_idempotency
        UNIQUE (wallet_id, idempotency_key),

    CONSTRAINT chk_external_bank_transfer_amount
        CHECK (amount > 0),

    CONSTRAINT chk_external_bank_transfer_fee
        CHECK (fee >= 0),

    CONSTRAINT chk_external_bank_transfer_status
        CHECK (status IN ('RESERVED', 'PENDING', 'SUCCESSFUL', 'FAILED', 'REVERSED'))
);

CREATE INDEX idx_external_bank_transfer_reference
    ON external_bank_transfers(reference);

CREATE INDEX idx_external_bank_transfer_wallet
    ON external_bank_transfers(wallet_id);

CREATE INDEX idx_external_bank_transfer_provider_reference
    ON external_bank_transfers(provider_reference);

CREATE INDEX idx_external_bank_transfer_created
    ON external_bank_transfers(created_at);

-- External transfer funds move into a dedicated clearing liability while provider
-- outcome is unresolved. The final provider settlement asset leg is intentionally
-- not created until Interswitch confirms Wave Transakt's funding/settlement model.
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
    'SYSTEM:EXTERNAL_TRANSFER_CLEARING:NGN',
    'SYSTEM',
    'LIABILITY',
    NULL,
    'NGN'
)
ON CONFLICT (code) DO NOTHING;
