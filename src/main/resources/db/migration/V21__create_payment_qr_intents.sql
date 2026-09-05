CREATE TABLE payment_qr_intents (
                                    id UUID PRIMARY KEY,

                                    recipient_wallet_id UUID NOT NULL,

                                    nonce VARCHAR(64) NOT NULL,

                                    amount NUMERIC(19, 2) NOT NULL,

                                    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',

                                    description VARCHAR(255),

                                    status VARCHAR(20) NOT NULL,

                                    expires_at TIMESTAMP NOT NULL,

                                    consumed_at TIMESTAMP,

                                    consumed_idempotency_key VARCHAR(128),

                                    transaction_reference VARCHAR(50),

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_payment_qr_recipient_wallet
                                        FOREIGN KEY (recipient_wallet_id)
                                            REFERENCES wallets(id),

                                    CONSTRAINT fk_payment_qr_transaction_reference
                                        FOREIGN KEY (transaction_reference)
                                            REFERENCES transactions(reference),

                                    CONSTRAINT chk_payment_qr_amount_positive
                                        CHECK (amount > 0),

                                    CONSTRAINT chk_payment_qr_status
                                        CHECK (
                                            status IN (
                                                       'ACTIVE',
                                                       'CONSUMED',
                                                       'EXPIRED',
                                                       'CANCELLED'
                                                )
                                            )
);

CREATE UNIQUE INDEX ux_payment_qr_nonce
    ON payment_qr_intents(nonce);

CREATE UNIQUE INDEX ux_payment_qr_transaction_reference
    ON payment_qr_intents(transaction_reference)
    WHERE transaction_reference IS NOT NULL;

CREATE INDEX idx_payment_qr_recipient
    ON payment_qr_intents(recipient_wallet_id);

CREATE INDEX idx_payment_qr_status_expiry
    ON payment_qr_intents(status, expires_at);