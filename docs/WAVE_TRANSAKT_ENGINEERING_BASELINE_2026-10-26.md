# WAVE TRANSAKT — engineering source of truth
Date: 2026-10-26. Read-only reconciliation of prior project decisions against connected GitHub and Render evidence.

## Project identity and product boundaries
- WAVE TRANSAKT LTD, RC 9915202, is a co-founded company (founder/technical builder and co-founder Sumaiyah). First intended launch market: Lagos, Nigeria.
- Product: Wave Personal Android customer app, Wave Business merchant QR, separate Wave POS Android app; same Spring Boot API. Later: native iOS, contactless cards, SmartPOS/acquiring and broader services **only after verified provider contracts and transaction controls**.
- QR-first personal and merchant identity/resolution, real receipts, reconciliation and provider-backed settlement are the initial product focus. Do not represent unfinished bill-pay, physical cards, or transfers as live.
- Historical visual baseline: Sep 10 2026 customer home with blue header and blue wallet card.
- **Latest explicitly requested design**: user's 18-screen dark navy/black and bright-cyan/blue reference board (splash, onboarding, registration/KYC, PIN/face setup, biometric/PIN login, home, services/flight example, wallet QR, settings, security, success, themes). This is a *new design target*, **not the same** as the historical blue/white dashboard. A successful Gradle build is not proof of screenshot fidelity.
- No fabricated balances, fake transactions, fabricated success alerts or bypassed verification. Only server-confirmed successful financial operations can open transaction-success screens.

## GitHub / Android evidence
- Customer repository: `Mams62/Wave_Transakt_android`.
- Master currently includes the Sep 10 customer recovery and secure PIN -> OTP -> face authentication merged with PR #31 (commit `d45eecc4a38e8edd537881ca9dc317585cb2e3f4`).
- UI work in PR #32 / branch `feat/unified-wave-blue-customer-ui-staging-20261026`; CI run #40244788373 PASSED Android debug assembly and unit tests. This PR is mainly a blue-and-white visual standardization, so **do not assume it implements the latest dark-cyan target**. It is not established as approved for merging.
- Older staging/customer/POS code and branches should remain accessible. Never reset or force-push away working history.
- Android Studio on user's Windows machine is not remotely accessible through GitHub. Local pulling, Gradle sync and device install must be verified on the device. Do not claim IDE synchronization or a delivered APK from successful source commits alone.

## Backend, payments and authentication
- Backend repo: `Mams62/Wave_Transakt_API` (Spring Boot 3.5.4 / Java 21 / PostgreSQL).
- Render staging deployment branch: `controlled-start-api-20260910`; auto-deploys *that branch* on commit, so do not write to that branch without approval.
- Login contract: 6-digit account PIN -> registered-phone OTP -> server-backed face/liveness verification -> authenticated JWT. The initial credential response may have `requiresOtp=true` and `token=null`; treating it as failed/complete login is wrong.
- If the identity provider is unavailable, setup-only onboarding and financial lockout remain separate from authenticated customer-wallet access.
- Keep API-side financial kill switches OFF until partner approvals, KYC/AML, genuine settlement, reconciliation, idempotency, signed webhooks and operational testing. In particular, no local money creation, legacy Paystack crediting, unverified QR payouts, or transaction simulation. Never place provider secrets or database credentials in Android.
- Wema API conversations and Interswitch wallet/acquiring/SmartPOS/card programs are **not** proof of live regulatory or partner authorization.

## Render evidence as of October 26
- Workspace: previously established `tea-dah1iept0dsc73e6tmqg` (Render My Workspace).
- Web service: `srv-dah1lcpt0dsc73e785eg` / `wave-transakt-api-staging`, Frankfurt region, Free plan.
- Public URL: `https://wave-transakt-api-staging.onrender.com` and `/api/health`.
- Render latest successful deployment recorded October 24 on backend commit `2d2cf505434f067727fe59913885557a54b8b2e7`.
- Render logs October 25: 47 Flyway migrations verified; schema version 119; PostgreSQL/Hikari connected; Spring Boot started in **153.501 seconds**. Render also recorded repeated non-zero (exit 1) service failures on October 22; specific failure cause not yet confirmed.
- Independent GitHub Actions request October 25 23:32 UTC: health check HTTP 200 `{"service":"Wave Transakt API","status":"UP"}`, total 25.626755 seconds. Successful health on that occasion does NOT demonstrate always-on availability or a complete login flow.
- Render Free web services spin down after 15 minutes idle. Raising Android login read timeout from 30 seconds to 75 seconds cannot solve a 153.5-second API cold start or repeated process exits.
- Database `dpg-dbedsfnb9u1s739covv0-a` / `wave-transakt-staging-db` is available, Free, created Oct 24 2026 and expires **Nov 23 2026 at 16:35 UTC**.
- `Flyway up to date` is **not evidence** that old users, wallets, ledger rows or merchant records were recovered. Original-data restoration/record reconciliation is not verified. Direct read-only SQL through the connector was blocked because the database IP allowlist is empty. Do not open public access merely to ease debugging.
- No verified separate production API and production DB; domain remains unregistered.

## Agreed hosting and operational constraints
- Render-first, strict recurring cap **below US$100/month**, recover-original database/data, register domain in a later approved stage.
- **Explicit authorization required before any hosting, billing, domain/DNS, database, migration, deploy, networking, production, or destructive change.** Preserve all existing database instances until old data is reconciled and a tested backup and rollback exist.
- A Koyeb/Neon staging concept was discussed as an isolated alternative; it must not silently become production or replace the original Render database.
- Read-only audit, source-level fixes on **non-deployed branches**, tests and documentation are allowed. No secrets copied into commits, UI, diagnostic logs or handoffs.

## Prioritized engineering backlog
P0. Inventory original vs active database and backup/recovery evidence; protect records before Nov 23 expiration. Current DB query access is restricted; use authorized operator-only backup/export with verified restore, not blanket IP allowlists.
P0. Stabilize staging availability: use measured startup logs and Render exit events; assess paid always-on app compute within the <$100 limit, *after explicit approval* and data backup. Do not treat longer Android timeouts as the root fix.
P0. Verify end-to-end PIN -> OTP -> provider-backed liveness -> JWT -> /api/auth/me on a physical phone. Test error codes/timeouts without sharing credentials or face/identity captures in logs.
P0. Enforce backend Wema/restriction kill switches and wallet correctness, and review every legacy funding/transfer entry point.
P1. Rebuild the actual *dark navy / cyan 18-screen* customer UI from the latest screenshot, screen by screen, retaining real existing navigation and API integration. Confirm screenshot-level QA across narrow Android devices and light/dark behavior.
P1. POS merchant QR checkout and verified receipts, merchant reconciliation, settlement reporting.
P2. Provider contracts/UAT for bank transfers, Interswitch SmartPOS, tap cards; native iOS later.
P2. Production deployment, domain registration, monitoring, alerting, release signing after staging and data-recovery evidence.

## Quality gates
- Gradle customer debug APK + unit tests in CI; test physical Android device and older OS support.
- Backend build and tests, secure API contract tests for auth and payment restrictions; health check under realistic cold/warm conditions.
- Original-data counts/backup integrity verified; rollback possible.
- Explicit review approval before merging reference-UI work or deploying backend changes.
- No "production ready", live funds or banking integration claims without audit evidence.

## October 26 follow-up: connected Railway and Neon evidence
- Railway has **two** existing `wave-transakt-staging` projects, both with one live `wave-transakt-api-staging` service:
  - Project `77c5e234-d054-4969-95b5-51c44783741f`; service `5336a6ce-1196-42b9-ac80-88518cfeacee`; public domain `wave-transakt-api-staging-staging-e587.up.railway.app`.
  - Project `dca9dbea-2b91-4cb5-aabe-c42f49e571f7`; service `ba376a42-da41-4999-9a94-a2ce1c1f9bf8`; public domain `wave-transakt-api-staging-staging.up.railway.app`.
  - Both deploy the same Oct 12 source commit `2d2cf505434f067727fe59913885557a54b8b2e7` in the US-East `iad` region; no Railway PostgreSQL service or volume is listed in either project.
  - Both report online, but Hikari logs include repeated `Failed to validate connection (This connection has been closed.)` warnings. Neither has been verified for authenticated login or original-wallet data access. The target database behind their existing `WAVE_DB_URL` environment values has **not** been identified; secrets were not read.
- Neon connected project `rapid-glitter-03986684`, `wave-transakt-staging-alt`, EU Frankfurt region, has a main database named `wave_transakt_staging` with 32 public tables and 47 successful migrations. **Read-only count query returned zero** users, zero wallets, zero merchants, zero legacy transactions, zero wallet transactions and zero ledger transactions. This is a populated *schema*, not evidence of rescued original financial records.
- A separate source-only draft PR `fix/staging-postgres-pool-resilience-20261026` adjusts Hikari connection lifetime, idle timeout and small pool sizes via environment overrides. Backend CI passed; **never merge/deploy without approved backup, credential/database reconciliation and release authorization.**
- Railway has an Amsterdam region `europe-west4-drams3a`; comparing it to US East is appropriate if colocating API with Frankfurt PostgreSQL, but migration/region changes require approval and endpoint tests.
- Android dark-splash implementation branch: `feat/wave-dark-reference-splash-onboarding-20261026`, review PR #33. This branch adds vector lightning, dark splash/launch gate, welcome/QR-first onboarding, three registration steps, and PIN/OTP login styling. The remaining 18-screen board is not complete; don't merge the older blue/white PR #32 as a substitute.
- Do not change either Railway project or rewire Android debug base URL to Railway until health probes, login contract checks, DB ownership/records and rollback paths are verified.
