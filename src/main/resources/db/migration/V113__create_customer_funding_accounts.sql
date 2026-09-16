CREATE TABLE customer_funding_accounts (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    provider_code VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_account_reference VARCHAR(120),
    account_number VARCHAR(32),
    bank_code VARCHAR(20),
    bank_name VARCHAR(120),
    account_name VARCHAR(180),
    provider_message VARCHAR(255),
    activated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_customer_funding_account_wallet_provider UNIQUE (wallet_id, provider_code),
    CONSTRAINT uq_customer_funding_account_provider_reference UNIQUE (provider_code, provider_account_reference),
    CONSTRAINT uq_customer_funding_account_provider_number UNIQUE (provider_code, account_number)
);

CREATE INDEX idx_customer_funding_account_wallet
    ON customer_funding_accounts(wallet_id);

CREATE INDEX idx_customer_funding_account_status
    ON customer_funding_accounts(status);

CREATE TABLE customer_funding_events (
    id UUID PRIMARY KEY,
    funding_account_id UUID REFERENCES customer_funding_accounts(id),
    provider_code VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(160) NOT NULL,
    provider_reference VARCHAR(160),
    status VARCHAR(40) NOT NULL,
    amount NUMERIC(19,2),
    currency VARCHAR(3),
    received_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    credited_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_customer_funding_event_provider_event UNIQUE (provider_code, provider_event_id)
);

CREATE INDEX idx_customer_funding_event_account
    ON customer_funding_events(funding_account_id);

CREATE INDEX idx_customer_funding_event_reference
    ON customer_funding_events(provider_code, provider_reference);

CREATE INDEX idx_customer_funding_event_status
    ON customer_funding_events(status);
