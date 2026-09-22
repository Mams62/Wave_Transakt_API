-- Make the Wave customer-facing account/wallet number the customer's
-- canonical Nigerian phone number (0XXXXXXXXXX).
--
-- Provider-issued bank/DVA account numbers remain separate and are not changed.
-- This migration fails closed if legacy phone data is unsupported or would
-- collapse two users onto the same canonical phone identity.

DO $$
DECLARE
    unsupported_count BIGINT;
    duplicate_groups BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO unsupported_count
    FROM users
    WHERE CASE
        WHEN BTRIM(phone) ~ '^0[0-9]{10}$' THEN BTRIM(phone)
        WHEN BTRIM(phone) ~ '^\\+234[0-9]{10}$' THEN '0' || SUBSTRING(BTRIM(phone) FROM 5)
        WHEN BTRIM(phone) ~ '^234[0-9]{10}$' THEN '0' || SUBSTRING(BTRIM(phone) FROM 4)
        WHEN BTRIM(phone) ~ '^[0-9]{10}$' THEN '0' || BTRIM(phone)
        ELSE NULL
    END IS NULL;

    IF unsupported_count > 0 THEN
        RAISE EXCEPTION
            'Cannot migrate Wave wallet numbers: % user phone value(s) are not supported Nigerian formats',
            unsupported_count;
    END IF;

    SELECT COUNT(*)
    INTO duplicate_groups
    FROM (
        SELECT canonical_phone
        FROM (
            SELECT CASE
                WHEN BTRIM(phone) ~ '^0[0-9]{10}$' THEN BTRIM(phone)
                WHEN BTRIM(phone) ~ '^\\+234[0-9]{10}$' THEN '0' || SUBSTRING(BTRIM(phone) FROM 5)
                WHEN BTRIM(phone) ~ '^234[0-9]{10}$' THEN '0' || SUBSTRING(BTRIM(phone) FROM 4)
                WHEN BTRIM(phone) ~ '^[0-9]{10}$' THEN '0' || BTRIM(phone)
            END AS canonical_phone
            FROM users
        ) normalized
        GROUP BY canonical_phone
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION
            'Cannot migrate Wave wallet numbers: % duplicate canonical phone identity group(s) found',
            duplicate_groups;
    END IF;
END $$;

WITH normalized AS (
    SELECT
        id,
        CASE
            WHEN BTRIM(phone) ~ '^0[0-9]{10}$' THEN BTRIM(phone)
            WHEN BTRIM(phone) ~ '^\\+234[0-9]{10}$' THEN '0' || SUBSTRING(BTRIM(phone) FROM 5)
            WHEN BTRIM(phone) ~ '^234[0-9]{10}$' THEN '0' || SUBSTRING(BTRIM(phone) FROM 4)
            WHEN BTRIM(phone) ~ '^[0-9]{10}$' THEN '0' || BTRIM(phone)
        END AS canonical_phone
    FROM users
)
UPDATE users u
SET
    phone = n.canonical_phone,
    updated_at = CURRENT_TIMESTAMP
FROM normalized n
WHERE u.id = n.id
  AND u.phone <> n.canonical_phone;

-- Move every legacy wallet number into a temporary non-phone namespace first.
-- This prevents a transient unique-key collision if an old generated wallet
-- number happens to equal another user's phone number.
UPDATE wallets
SET
    wallet_number = 'T' || SUBSTRING(MD5(id::text) FROM 1 FOR 19),
    updated_at = CURRENT_TIMESTAMP;

UPDATE wallets w
SET
    wallet_number = u.phone,
    updated_at = CURRENT_TIMESTAMP
FROM users u
WHERE u.id = w.user_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM wallets w
        JOIN users u ON u.id = w.user_id
        WHERE w.wallet_number <> u.phone
    ) THEN
        RAISE EXCEPTION
            'Wave wallet-number migration did not converge to user phone identity';
    END IF;
END $$;
