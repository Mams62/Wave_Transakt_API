ALTER TABLE transactions
    ADD COLUMN idempotency_key VARCHAR(128);

ALTER TABLE transactions
    ADD COLUMN request_fingerprint VARCHAR(64);

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_idempotency_fields
        CHECK (
            (idempotency_key IS NULL AND request_fingerprint IS NULL)
                OR
            (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL)
            );

CREATE UNIQUE INDEX ux_transactions_sender_idempotency_key
    ON transactions (sender_wallet_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;