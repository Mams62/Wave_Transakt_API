CREATE TABLE ledger_accounts (
                                 id UUID PRIMARY KEY,

                                 code VARCHAR(100) NOT NULL UNIQUE,

                                 account_type VARCHAR(20) NOT NULL,

                                 account_class VARCHAR(20) NOT NULL,

                                 wallet_id UUID UNIQUE,

                                 currency VARCHAR(3) NOT NULL,

                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_ledger_account_wallet
                                     FOREIGN KEY (wallet_id)
                                         REFERENCES wallets(id),

                                 CONSTRAINT chk_ledger_account_type
                                     CHECK (
                                         account_type IN (
                                                          'WALLET',
                                                          'SYSTEM'
                                             )
                                         ),

                                 CONSTRAINT chk_ledger_account_class
                                     CHECK (
                                         account_class IN (
                                                           'ASSET',
                                                           'LIABILITY',
                                                           'EQUITY',
                                                           'REVENUE',
                                                           'EXPENSE'
                                             )
                                         ),

                                 CONSTRAINT chk_ledger_wallet_link
                                     CHECK (
                                         (account_type = 'WALLET' AND wallet_id IS NOT NULL)
                                             OR
                                         (account_type = 'SYSTEM' AND wallet_id IS NULL)
                                         )
);


CREATE TABLE ledger_transactions (
                                     id UUID PRIMARY KEY,

                                     reference VARCHAR(100) NOT NULL UNIQUE,

                                     event_type VARCHAR(40) NOT NULL,

                                     currency VARCHAR(3) NOT NULL,

                                     description VARCHAR(255),

                                     reversal_of_id UUID,

                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_ledger_transaction_reversal
                                         FOREIGN KEY (reversal_of_id)
                                             REFERENCES ledger_transactions(id)
);


CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,

                                ledger_transaction_id UUID NOT NULL,

                                account_id UUID NOT NULL,

                                line_no SMALLINT NOT NULL,

                                direction VARCHAR(10) NOT NULL,

                                amount NUMERIC(19, 2) NOT NULL,

                                currency VARCHAR(3) NOT NULL,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_ledger_entry_transaction
                                    FOREIGN KEY (ledger_transaction_id)
                                        REFERENCES ledger_transactions(id),

                                CONSTRAINT fk_ledger_entry_account
                                    FOREIGN KEY (account_id)
                                        REFERENCES ledger_accounts(id),

                                CONSTRAINT chk_ledger_entry_direction
                                    CHECK (
                                        direction IN (
                                                      'DEBIT',
                                                      'CREDIT'
                                            )
                                        ),

                                CONSTRAINT chk_ledger_entry_amount
                                    CHECK (amount > 0),

                                CONSTRAINT ux_ledger_transaction_line
                                    UNIQUE (
                                            ledger_transaction_id,
                                            line_no
                                        )
);


CREATE INDEX idx_ledger_accounts_wallet
    ON ledger_accounts(wallet_id);

CREATE INDEX idx_ledger_entries_account
    ON ledger_entries(account_id);

CREATE INDEX idx_ledger_entries_transaction
    ON ledger_entries(ledger_transaction_id);

CREATE INDEX idx_ledger_transactions_created
    ON ledger_transactions(created_at);


-- ============================================================
-- MIGRATION SUSPENSE ACCOUNT
-- ============================================================

INSERT INTO ledger_accounts (
    id,
    code,
    account_type,
    account_class,
    wallet_id,
    currency
)
VALUES (
           gen_random_uuid(),
           'SYSTEM:MIGRATION_SUSPENSE:NGN',
           'SYSTEM',
           'EQUITY',
           NULL,
           'NGN'
       );


-- ============================================================
-- CREATE A LEDGER ACCOUNT FOR EVERY EXISTING WALLET
-- ============================================================

INSERT INTO ledger_accounts (
    id,
    code,
    account_type,
    account_class,
    wallet_id,
    currency
)
SELECT
    gen_random_uuid(),
    'WALLET:' || w.id::text,
    'WALLET',
    'LIABILITY',
    w.id,
    w.currency
FROM wallets w;


-- ============================================================
-- OPENING BALANCE JOURNAL
--
-- This converts existing wallet.balance values into ledger
-- history so the new ledger begins from the exact current
-- financial state.
-- ============================================================

INSERT INTO ledger_transactions (
    id,
    reference,
    event_type,
    currency,
    description
)
SELECT
    gen_random_uuid(),
    'OPENING:' || w.id::text,
    'OPENING_BALANCE',
    w.currency,
    'Opening balance migrated from wallet balance'
FROM wallets w
WHERE w.balance <> 0;


-- Counter-entry into migration suspense.
INSERT INTO ledger_entries (
    id,
    ledger_transaction_id,
    account_id,
    line_no,
    direction,
    amount,
    currency
)
SELECT
    gen_random_uuid(),
    lt.id,
    suspense.id,
    1,

    CASE
        WHEN w.balance > 0
            THEN 'DEBIT'
        ELSE 'CREDIT'
        END,

    ABS(w.balance),

    w.currency

FROM wallets w

         JOIN ledger_transactions lt
              ON lt.reference =
                 'OPENING:' || w.id::text

         JOIN ledger_accounts suspense
              ON suspense.code =
                 'SYSTEM:MIGRATION_SUSPENSE:NGN'

WHERE w.balance <> 0;


-- Wallet side of opening balance.
INSERT INTO ledger_entries (
    id,
    ledger_transaction_id,
    account_id,
    line_no,
    direction,
    amount,
    currency
)
SELECT
    gen_random_uuid(),
    lt.id,
    wallet_account.id,
    2,

    CASE
        WHEN w.balance > 0
            THEN 'CREDIT'
        ELSE 'DEBIT'
        END,

    ABS(w.balance),

    w.currency

FROM
    wallets w

         JOIN ledger_transactions lt
              ON lt.reference =
                 'OPENING:' || w.id::text

         JOIN ledger_accounts wallet_account
              ON wallet_account.wallet_id = w.id

WHERE w.balance <> 0;