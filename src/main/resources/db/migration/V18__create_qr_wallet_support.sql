-- Wave Transakt
-- V18
-- QR wallet support

CREATE INDEX IF NOT EXISTS idx_wallets_wallet_number_qr
    ON wallets(wallet_number);

CREATE INDEX IF NOT EXISTS idx_wallets_status_qr
    ON wallets(status);