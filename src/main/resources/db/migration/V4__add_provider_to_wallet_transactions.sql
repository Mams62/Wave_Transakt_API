ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50);