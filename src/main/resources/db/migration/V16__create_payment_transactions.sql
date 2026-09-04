CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL,

    reference VARCHAR(100) NOT NULL UNIQUE,

    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',

    provider VARCHAR(30) NOT NULL DEFAULT 'PAYSTACK',

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    provider_transaction_id VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_transaction_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_payment_transaction_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets(id)
);

CREATE INDEX idx_payment_transactions_user
    ON payment_transactions(user_id);

CREATE INDEX idx_payment_transactions_wallet
    ON payment_transactions(wallet_id);

CREATE INDEX idx_payment_transactions_status
    ON payment_transactions(status);
