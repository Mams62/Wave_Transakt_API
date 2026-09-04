CREATE TABLE wallets (

                         id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

                         user_id UUID NOT NULL,

                         wallet_number VARCHAR(20) UNIQUE NOT NULL,

                         available_balance NUMERIC(18,2) DEFAULT 0,

                         ledger_balance NUMERIC(18,2) DEFAULT 0,

                         currency VARCHAR(5) DEFAULT 'NGN',

                         status VARCHAR(20) DEFAULT 'ACTIVE',

                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_wallet_user
                             FOREIGN KEY(user_id)
                                 REFERENCES users(id)
);