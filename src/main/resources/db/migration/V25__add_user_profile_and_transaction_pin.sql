ALTER TABLE users
    ADD COLUMN IF NOT EXISTS state VARCHAR(80),
    ADD COLUMN IF NOT EXISTS local_government VARCHAR(100),
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS gender VARCHAR(30),
    ADD COLUMN IF NOT EXISTS transaction_pin_hash VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_users_state
    ON users(state);
