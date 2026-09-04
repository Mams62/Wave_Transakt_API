CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,

                              reference VARCHAR(100) NOT NULL UNIQUE,

                              sender_user_id BIGINT,
                              receiver_user_id BIGINT,

                              sender_wallet_id BIGINT,
                              receiver_wallet_id BIGINT,

                              amount NUMERIC(19, 2) NOT NULL,
                              fee NUMERIC(19, 2) NOT NULL DEFAULT 0,
                              total_amount NUMERIC(19, 2) NOT NULL,

                              currency VARCHAR(10) NOT NULL DEFAULT 'NGN',

                              transaction_type VARCHAR(50) NOT NULL,
                              status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                              description VARCHAR(255),

                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_reference
    ON transactions(reference);

CREATE INDEX idx_transactions_sender_user
    ON transactions(sender_user_id);

CREATE INDEX idx_transactions_receiver_user
    ON transactions(receiver_user_id);

CREATE INDEX idx_transactions_status
    ON transactions(status);

CREATE INDEX idx_transactions_created_at
    ON transactions(created_at);