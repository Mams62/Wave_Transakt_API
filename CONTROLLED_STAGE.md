# Wave Transakt controlled stage

This repository's active controlled backend line is `controlled-start-api-20260910`.

The repository default branch `main` should be kept fast-forwarded to the same controlled backend history so GitHub, Render Blueprint discovery, and fresh clones do not fall back to an obsolete unrelated backend line.

## Render Blueprint

The canonical deployment definition is `render.yaml`.

It defines:

- web service: `wave-transakt-api-staging`
- service branch: `controlled-start-api-20260910`
- database: `wave-transakt-staging-db`
- region: `frankfurt`
- health check: `/api/health`
- deploy trigger: commits on the controlled branch

If the Render service or database is deleted, restore it by syncing/recreating the Blueprint from this repository instead of creating an unrelated manual service with different settings.

## Database wiring

Render Postgres values are supplied separately as host, port, database, user, and password. `application.yml` assembles them into the JDBC URL used by Spring Boot. A local/developer environment may still override the full value with `WAVE_DB_URL`.

Do not replace the JDBC URL with Render's plain `postgresql://...` connection string unless the application explicitly converts it to a JDBC URL.

## Controlled-stage safety switches

These must remain disabled until the corresponding provider settlement paths are implemented and tested end to end:

- `WAVE_LEGACY_PAYSTACK_ENABLED=false`
- `WAVE_LOCAL_MONEY_MOVEMENT_ENABLED=false`
- `WAVE_SERVICE_PAYMENTS_ENABLED=false`

The user-facing wallet source for the controlled build is Wema. Do not make a failing UI button appear successful by mutating a local wallet projection or returning fake financial data.

## Wema

Wallet/account onboarding uses the Wema wallet gateway configured through the `WAVE_WEMA_WALLET_*` variables. Bank-transfer products use the separate `WAVE_WEMA_TRANSFER_*` variables.

Credentials are intentionally marked `sync: false` in `render.yaml`. Their values belong in Render's secret environment settings and must never be committed to Git.

If a service is recreated, verify that all `sync: false` Wema and VTpass values are present before testing provider calls.

## Authentication

The controlled API uses a six-digit `accountPin` for login and a separate six-digit `transactionPin` for payment authorization. BVN and NIN are required at registration for the controlled identity/wallet flow.

`WAVE_JWT_SECRET` signs login tokens. If the Render service is recreated with a newly generated JWT secret, previously issued Android tokens become invalid. The Android app should clear the stale session and require a fresh login rather than displaying a persistent raw `Forbidden` error.

## Email verification

`WAVE_RETURN_VERIFICATION_CODE=true` is allowed only on the controlled staging environment while outbound email delivery is not connected. Production must set it to `false` and complete verification through an actual delivery channel.

## Health check

A healthy deployment must return a successful response from:

`GET /api/health`

Do not enable money movement merely to make the health check or staging smoke test pass.
