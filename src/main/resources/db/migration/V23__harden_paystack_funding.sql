CREATE UNIQUE INDEX ux_payment_transactions_provider_transaction_id
    ON payment_transactions(provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;


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
    'SYSTEM:PAYSTACK_SETTLEMENT:NGN',
    'SYSTEM',
    'ASSET',
    NULL,
    'NGN'
WHERE NOT EXISTS (
    SELECT 1
    FROM ledger_accounts
    WHERE code = 'SYSTEM:PAYSTACK_SETTLEMENT:NGN'
);