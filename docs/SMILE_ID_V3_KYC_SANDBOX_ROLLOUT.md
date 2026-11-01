# Smile ID V3 sandbox KYC for Wave Transakt — controlled draft

**Status (2026-11-01):** Sandbox API submission adapter is present but **disabled**. No Smile ID account, partner credentials, verification job, callback, approval, KYC database state, or production rollout has been confirmed in this work.

## Purpose

- Offer a server-side, sandbox-only adapter for Smile ID V3 Enhanced KYC for Nigeria (`NG`), using `BVN` or `NIN` IDs.
- Exchange server-held partner ID/API key for a short-lived JWT via `POST /v3/token`.
- Submit `multipart/form-data` at `POST /v3/enhanced_kyc` with consent, provider-approved identity details, stable Wave customer ID and a unique Wave KYC attempt reference.
- Treat **HTTP 202 / Accepted** as `PENDING` only; never equate job submission with a verified identity.
- Keep sensitive request/response content and provider secrets out of Android, logs, GitHub, exceptions and user-facing data.
- Do not change the active Dojah-based flows, auth or wallet/transfer restrictions in this draft.

The adapter can be invoked only by future *server-side* KYC orchestration. This draft creates **no public submission endpoint**. It does not register new customers or activate wallets.

## Configuration — backend environment ONLY

```properties
WAVE_SMILE_ID_SANDBOX_ENABLED=false
WAVE_SMILE_ID_PARTNER_ID=
WAVE_SMILE_ID_API_KEY=
WAVE_SMILE_ID_CALLBACK_URL=
WAVE_SMILE_ID_PRIVACY_POLICY_URL=
```

Default is disabled. URLs must be HTTPS, controlled by Wave Transakt and accepted by Smile ID. Code is *hardwired to Smile ID sandbox*: `https://api.sandbox.smileidentity.com`. The code intentionally does **not** provide a production endpoint toggle.

Do not put the actual values in pull requests or app resources.

## Before enabling even in staging

1. A Wave Transakt authorized company representative must open/verify a Smile ID partner account and obtain **sandbox** credentials and permission for Nigerian BVN/NIN Enhanced KYC.
2. Confirm the current official Smile ID V3 contract, sanctioned sandbox test identifiers, privacy/consent requirements, callback allowlisting, any required per-request HMAC option, and fee/volume terms. If HMAC is mandatory for Wave's account, adapt and test the client **before** enabling.
3. Publish an approved privacy notice and get explicit verifiable consent prior to sharing personal data with Smile ID. Establish NDPA/NDPC compliance obligations and licensed bank/payment partner workflow.
4. Implement persistent `KYC_PENDING` records with stable unique attempt IDs, idempotent submission, immutable audit references and data retention/deletion rules; avoid raw identifiers in logs and unencrypted database fields.
5. Implement an authenticated Smile ID callback receiver with signature validation, timestamp/replay protection, expected partner/user/attempt matching, strict final outcome mapping, and review/denial handling. Never trust a client-submitted callback or auto-approve provisional outcomes.
6. Incorporate selfie/liveness proof and identity match, if required for Wave's account tier, using provider-approved SDK and server-side verification.
7. Refactor registration and email activation: never create a spendable wallet or activate an account before all mandatory KYC + phone/email verification checks complete; leave money movement disabled until partner/product approval.
8. Validate with **only Smile ID's approved sandbox data**, negative responses, network timeouts, duplicate callbacks, races, and end-to-end Android flow. Complete CI and an isolated database migration dry run before considering deploy.

## Current engineering boundary

`SmileIdEnhancedKycClient.submitEnhancedKyc()` uses credentials from the backend environment and returns `PendingJob(jobId, userId, "PENDING")` on accepted submission. This **is not a complete live KYC flow**. It deliberately makes no changes to `User.accountStatus`, `bvnVerified`, `ninVerified`, `Wallet` or payment feature switches.

The pre-existing KYC/registration logic on this branch is unchanged and must be reconciled with the other draft identity PRs before staging merge.

References:
- https://docs.usesmileid.com/api-reference
- https://smile.id/countries/nigeria
- https://docs.usesmileid.com/
