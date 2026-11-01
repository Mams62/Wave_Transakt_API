-- Optional public handles; does not modify wallet numbers, KYC, balances or ledger.
-- New verified registrations receive a handle. Existing customers activate later.
ALTER TABLE users ADD COLUMN wave_id VARCHAR(8);
ALTER TABLE users ADD CONSTRAINT chk_users_wave_id_format
    CHECK (wave_id IS NULL OR wave_id ~ '^[A-Z]{4}[0-9]{2,4}$');
CREATE UNIQUE INDEX ux_users_wave_id ON users (wave_id);
