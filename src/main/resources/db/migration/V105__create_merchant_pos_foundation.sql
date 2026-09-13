CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    merchant_code VARCHAR(40) NOT NULL UNIQUE,
    business_name VARCHAR(160) NOT NULL,
    business_type VARCHAR(60) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_code VARCHAR(40),
    provider_merchant_id VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchants_owner_user_id ON merchants(owner_user_id);
CREATE INDEX idx_merchants_status ON merchants(status);

CREATE TABLE merchant_qr_addresses (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL UNIQUE REFERENCES merchants(id),
    public_id VARCHAR(80) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchant_qr_public_id ON merchant_qr_addresses(public_id);

CREATE TABLE pos_terminals (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    terminal_code VARCHAR(60) NOT NULL UNIQUE,
    provider_code VARCHAR(40),
    provider_terminal_id VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    supports_qr BOOLEAN NOT NULL DEFAULT TRUE,
    supports_nfc BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pos_terminals_merchant_id ON pos_terminals(merchant_id);

CREATE TABLE merchant_payments (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    terminal_id UUID REFERENCES pos_terminals(id),
    payment_reference VARCHAR(80) NOT NULL UNIQUE,
    provider_code VARCHAR(40),
    provider_reference VARCHAR(160),
    channel VARCHAR(40) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    status VARCHAR(40) NOT NULL,
    receipt_number VARCHAR(80) UNIQUE,
    settlement_status VARCHAR(40) NOT NULL DEFAULT 'NOT_READY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchant_payments_merchant_id ON merchant_payments(merchant_id);
CREATE INDEX idx_merchant_payments_terminal_id ON merchant_payments(terminal_id);
CREATE INDEX idx_merchant_payments_status ON merchant_payments(status);
CREATE INDEX idx_merchant_payments_settlement_status ON merchant_payments(settlement_status);
