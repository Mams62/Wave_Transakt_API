-- Move only untouched local wallet records to the new Interswitch provider.
-- Wallets that already have Wema onboarding/provider data are deliberately
-- preserved so an existing external account is never silently reassigned.
UPDATE wallets
SET provider = 'INTERSWITCH',
    updated_at = CURRENT_TIMESTAMP
WHERE provider = 'WEMA'
  AND provider_status = 'NOT_STARTED'
  AND provider_account_number IS NULL
  AND provider_tracking_id IS NULL;
