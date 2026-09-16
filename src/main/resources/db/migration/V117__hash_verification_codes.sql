ALTER TABLE verification_codes
    ADD COLUMN code_hash VARCHAR(100);

ALTER TABLE verification_codes
    ALTER COLUMN code DROP NOT NULL;

-- Historical used codes no longer need any recoverable secret value.
UPDATE verification_codes
SET code = NULL
WHERE used = TRUE;

-- Codes are never looked up by their secret value; remove the plaintext index.
DROP INDEX IF EXISTS idx_verification_codes_code;

ALTER TABLE verification_codes
    ADD CONSTRAINT chk_verification_codes_secret_state
    CHECK (
        used = TRUE
        OR (
            (code IS NOT NULL AND code_hash IS NULL)
            OR (code IS NULL AND code_hash IS NOT NULL)
        )
    );
