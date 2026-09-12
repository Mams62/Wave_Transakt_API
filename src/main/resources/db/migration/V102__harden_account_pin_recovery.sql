ALTER TABLE password_reset_tokens
    ALTER COLUMN code TYPE VARCHAR(100);

DROP INDEX IF EXISTS idx_password_reset_code;
