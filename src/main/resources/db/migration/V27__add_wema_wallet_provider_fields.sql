ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30) NOT NULL DEFAULT 'WEMA',
    ADD COLUMN IF NOT EXISTS provider_account_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS provider_tracking_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS provider_message VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_last_synced_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_wallets_provider_account_number
    ON wallets(provider_account_number)
    WHERE provider_account_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wallets_provider_status
    ON wallets(provider_status);
