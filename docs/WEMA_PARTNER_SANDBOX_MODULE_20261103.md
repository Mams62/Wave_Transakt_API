# Wema onboarding integration: simulation-only foundation (3 Nov 2026)

**Status: DRAFT / NOT CONNECTED TO WEMA / NOT APPROVED FOR PROD.**

This branch builds on unmerged PR #62 (Wave ID) and is NOT a replacement for
the separate KYC/OTP provider readiness work in PR #58 or other provider PRs.
Never merge to deployment branches without CI, backups, staging migration
validation, and a documented bank partner sandbox contract.

## What is implemented

- Authenticated customer JWT/ACCESS_FULL: `GET /api/v1/partner/sandbox/readiness`.
- Authenticated `POST /api/v1/partner/sandbox/intents`, requiring
  `Idempotency-Key` (16-128 URL-safe characters) and JSON
  `{"amountKobo":12500,"merchantReference":"ORDER-XYZ"}`.
  **DISABLED** unless `wave.bank.wema.sandbox-simulation-enabled=true`;
  DB unique actor+key handles multi-instance races. A retry of the same key and
  body returns the same simulated reference, a changed payload gives HTTP 409.
- Authenticated `GET /api/v1/partner/sandbox/intents/{reference}` is owner scoped.
- Internal simulation-only callback `POST /api/v1/webhooks/wema/simulation`:
  `X-Wave-Sandbox-Event-Id`, `X-Wave-Sandbox-Timestamp` (UTC UNIX seconds),
  `X-Wave-Sandbox-Signature` (hex HMAC-SHA256 over
  `timestamp + "." + exact raw HTTP body`), JSON
  `{"reference":"SIM-<uuid>","amountKobo":12500,"currency":"NGN","status":"PENDING|CONFIRMED|FAILED"}`.
  Body max 32 KiB, timestamp age <= 300 seconds/future skew <= 30 sec.
  HMAC checks **before** recording a trusted event ID.
  PostgreSQL `ON CONFLICT DO NOTHING` provides cross-replica deduplication.
  This format belongs to our own LOCAL SIMULATOR and is **NOT Wema's documented
  signature protocol. NEVER register this URL at the bank.**
- Admin JWT role-gated `GET /api/v1/admin/wema/sandbox-reconciliation`
  counts unresolved/pending/test-verified states and orphan/mismatched events,
  without mutating ledger or balances.
- Explicit state machine `CREATED -> PENDING -> TEST_CONFIRMED | TEST_FAILED`,
  contradictory terminal events produce `REVIEW_REQUIRED`.
  All states and receipts always state `SIMULATION_ONLY_NO_MONEY`.
- Android Flow Studio companion branch
  `feat/flow-studio-wema-readiness-20261103` displays server-owned readiness in
  existing Wave Provider Status screen. No new send-money action is enabled.

## Application configuration

All default to closed/unavailable. Do NOT put credentials into Git or Android.

```text
wave.bank.wema.sandbox-simulation-enabled=false
wave.bank.wema.sandbox-webhook-secret=  # ONLY for local simulator; >=32 chars
```

Use environment `WAVE_BANK_WEMA_SANDBOX_SIMULATION_ENABLED` only if your config
maps it to the property; Spring's relaxed binding for custom dotted property
references varies by source. Explicitly define the dotted property in
`application.yml` when enabling, not production secrets in Git.

This foundation **does not call Wema**. Existing server-side Wema Wallet and
Transfer clients remain unchanged and still require bank-confirmed products,
hostnames, subscription keys, entitlement, KYC and execution approval. Existing
wallet local movement and external transfer kill switches stay OFF.

## Bank onboarding information needed to replace this simulator

1. Official Wema product list, assigned sandbox URL(s), non-production test data.
2. Authenticating scheme: subscription header vs OAuth/token, exact token lifetime,
   rotation, scope and credential provisioning, TLS/client certificates and IP allowlists.
3. Approved wallet issuance, bank/account name enquiry and transfer endpoint contracts.
4. Exact callback verification: signature/HMAC algorithm, timestamp, canonicalization,
   event ID, retry behaviour, source IP and event status semantics.
5. Definition of payment FINALITY, retry/inquiry/reversal, settlement/ledger matching,
   fee schedule, limits, reconciliation report and incident support.
6. KYC/AML, consent and account ownership/processing roles with Wema.
7. Approved operational and legal signoff before toggling *any* movement.

## Pre-release gating

- Build and unit test on CI; validate Flyway V121 on a NEW disposable Postgres.
- Keep WEMA real money execution disabled; do not use local simulated SUCCESS for
  confirmation animations, receipts, bank balance, merchant settlement or wallet credit.
- Add Postgres multi-instance concurrency and transaction rollback integration tests
  with Testcontainers before release. Test out-of-order, delayed, duplicate, tampered
  and conflicting callbacks.
- Implement official Wema authenticator/verifier after bank documentation arrives;
  require replay protections, official payment inquiry and finance-approved settlement.
- Verify Android restricted/legacy login, screen-reader states and old API 404 handling.
- Back up expiring Render database and test restoration independently of this PR.

**No production configuration, provider requests, infrastructure or customer
wallet balances have been changed by this branch.**
