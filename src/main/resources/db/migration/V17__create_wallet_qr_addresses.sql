CREATE TABLE wallet_qr_addresses (
                                     id UUID PRIMARY KEY,

                                     wallet_id UUID NOT NULL,

                                     qr_code VARCHAR(100) NOT NULL,

                                     active BOOLEAN NOT NULL DEFAULT TRUE,

                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_wallet_qr_wallet
                                         FOREIGN KEY (wallet_id)
                                             REFERENCES wallets(id)
                                             ON DELETE CASCADE,

                                     CONSTRAINT uk_wallet_qr_wallet
                                         UNIQUE (wallet_id),

                                     CONSTRAINT uk_wallet_qr_code
                                         UNIQUE (qr_code)
);

CREATE INDEX idx_wallet_qr_wallet_id
    ON wallet_qr_addresses(wallet_id);

CREATE INDEX idx_wallet_qr_code
    ON wallet_qr_addresses(qr_code);