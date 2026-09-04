CREATE TABLE wallet_transactions (
                                     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

                                     wallet_id UUID NOT NULL,

                                     reference VARCHAR(255) NOT NULL UNIQUE,

                                     type VARCHAR(50) NOT NULL,

                                     amount NUMERIC(19,2) NOT NULL,

                                     balance_before NUMERIC(19,2) NOT NULL,

                                     balance_after NUMERIC(19,2) NOT NULL,

                                     description VARCHAR(255),

                                     provider VARCHAR(255),

                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_wallet_transaction_wallet
                                         FOREIGN KEY (wallet_id)
                                             REFERENCES wallets(id)
);