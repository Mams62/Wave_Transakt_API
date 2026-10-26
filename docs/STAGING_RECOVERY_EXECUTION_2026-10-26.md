# Wave Transakt — approved staging recovery execution record

Date: October 26, 2026. Scope: staging only. This is an operational handoff,
not authorization to process real customer money.

## Completed — Railway staging A

- Existing Railway project: `77c5e234-d054-4969-95b5-51c44783741f`
- Existing staging environment: `bd1f97cd-16bd-4b51-8122-0d6cc7feec33`
- Existing API service: `5336a6ce-1196-42b9-ac80-88518cfeacee`
- Public staging API: `https://wave-transakt-api-staging-staging-e587.up.railway.app`
- API base for Android debug: `https://wave-transakt-api-staging-staging-e587.up.railway.app/api/`
- Deployed GitHub commit: `d237a57840765611c38b70be2641244e17b4928a`
- Backend branch (source pinned to this commit): `fix/staging-db-readiness-20261026`; PR #54.
- Actual deployment: `0ea8b797-6a4f-4dca-b107-1b221ca0b5c6`, SUCCESS.
- Healthcheck path: `/api/health/ready` — performs `SELECT 1`, returns READY only when connected to PostgreSQL. Deployment health check passed with HTTP 200.
- App startup: 14.257 seconds during the successful deployment.
- Region changed from IAD, Virginia, to `europe-west4-drams3a` (Amsterdam); one replica, no volume and no DNS change.
- Server-side safety switches explicitly set false for local money movement, external bank transfers, service payments, Interswitch wallet/acquiring/transfer/card-issuing approvals. `WAVE_RETURN_VERIFICATION_CODE=false`, `WAVE_RATE_LIMITS_ENABLED=true`. Values are intentionally not recorded as secrets.
- Backend CI passed for commit `d237a578...`, including tests and Docker build.
- Read-only external staging CI passed at `40252293459`: READY, unauthenticated `/api/auth/me` and `/api/wallet` correctly rejected (401).

## Database isolation, integrity and rollback

- Existing Railway A and B services' DNS connection target matched the existing Neon project `rapid-glitter-03986684` / database `wave_transakt_staging` in Frankfurt, **not the Render database**.
- Before and after the deployment, the connected Neon DB showed 47 successful Flyway migrations, zero users, zero wallets, zero merchants and zero ledger transactions. Do not represent this as recovered original customer data.
- Manual Neon snapshot created: `snap-shiny-boat-b9d1he6s` on branch `br-twilight-shadow-b91zzlfl`; retains the isolated Neon state before Android switchover. Restore only to a new branch after validating impact.
- Render PostgreSQL `dpg-dbedsfnb9u1s739covv0-a`: still available, Free plan, expires **2026-11-23T16:35:11Z**. Its external IP allowlist is empty and available Render tools cannot query/export it. Render's original DB ID `dpg-dah1ko1t0dsc73e75uc0-a` returned 404. **No original data backup or migration is verified**.
- Neither Render nor Railway staging B was deleted or repointed. Railway B remains a fallback, but has not been promoted.
- Rollback reference for Railway A: previously deployed code `2d2cf505434f067727fe59913885557a54b8b2e7`, deployment ID `ceedcbf7-2d32-4f31-82db-f1cee09e2dff`. Reconnect pinned source to this commit and restore old healthcheck only if needed, preserving safety flags and verifying environment/database unchanged. Re-run dependency checks after rollback.
- Any future database movement requires independently verified record counts, encrypted logical backup, tested restore and written cutover rollback plan.

## Android customer staging

- Current preview branch: `feat/wave-dark-staging-home-auth-20261026`; draft PR #34 stacked on dark onboarding PR #33.
- Implements dark Wave splash, onboarding, registration/PIN login, API-backed wallet home, profile and settings routing. Uses real API responses, no fake wallet balance.
- The Android debug API default was switched **only on the staging preview branch** to Railway A; `-PDEBUG_API_BASE_URL=...` remains an override.
- SessionManager binds each encrypted JWT to the configured API base URL. Installing the changed app signs out legacy Render sessions (without wiping user settings); tokens cannot cross independent staging databases.
- Android `master` and production release endpoint are not changed by these preview commits.
- Original Wema settlement restrictions remain enabled at API level (money movement disabled).

## Blocking completion gates

1. **Original-data preservation:** authorized backup/export and matching record-level validation on the Render DB before Nov 23; Render connector does not support plan upgrades or external queries with the current allowlist. Plan renewal requires the Render dashboard or an authorized admin workflow.
2. **Provider onboarding:** Railway has no configured Termii-compatible SMS API keys and no Dojah app/API keys in the inspected variable-name inventory; existing backend email verification has no configured outbound mail transport. Restore by approved provider accounts and secure secret configuration, **never** exposing codes in API responses or skipping BVN/NIN/face verification.
3. **Device acceptance:** Android Studio is local to user's Windows PC and is not remotely controlled by connected GitHub. Compile and install the staging debug variant, check sign-out on API switch, all 18 visual reference screens, onboarding/login and Wema lockout with provider-approved test records. CI does not verify these external integrations.
4. **Budget:** Railway and Neon billing totals not available through connected tools. Keep monitored usage under the previous strict $100/mo cap and avoid adding paid services without verifying invoice.
5. **Release:** Never call staging production-ready nor advertise live payments before identity, data restoration and regulated partner tests.

## Test commands (safe, no credentials)

```sh
curl -fsS --max-time 45 https://wave-transakt-api-staging-staging-e587.up.railway.app/api/health/ready
```

Expected: `{"service":"Wave Transakt API","status":"READY"}`.
Unauthenticated `/api/auth/me` and `/api/wallet` should return 401 (or 403), not customer records.
