DROP TABLE IF EXISTS transactions;

CREATE TABLE transactions (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              reference VARCHAR(50) NOT NULL UNIQUE,

                              sender_wallet_id UUID NOT NULL,
                              receiver_wallet_id UUID NOT NULL,

                              amount NUMERIC(19,2) NOT NULL,

                              currency VARCHAR(3) NOT NULL DEFAULT 'NGN',

                              type VARCHAR(30) NOT NULL,

                              status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                              description VARCHAR(255),

                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_transaction_sender_wallet
                                  FOREIGN KEY (sender_wallet_id)
                                      REFERENCES wallets(id),

                              CONSTRAINT fk_transaction_receiver_wallet
                                  FOREIGN KEY (receiver_wallet_id)
                                      REFERENCES wallets(id)
);

CREATE INDEX idx_transaction_reference
    ON transactions(reference);

CREATE INDEX idx_transaction_sender
    ON transactions(sender_wallet_id);

CREATE INDEX idx_transaction_receiver
    ON transactions(receiver_wallet_id);

CREATE INDEX idx_transaction_created_at
    ON transactions(created_at);