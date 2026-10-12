# Koyeb + Neon staging deployment (test only)

This guide creates a separate staging deployment. It does not replace, migrate, or delete the existing Render database or service. Do not use this staging environment for real customer funds or production credentials.

## Current source

- API source branch: `feat/koyeb-neon-staging-20261012`
- Runtime: Java 21 Spring Boot packaged by the existing root `Dockerfile`
- Health check: `/api/health`
- Listen port: `8080`
- Database project: `wave-transakt-staging-alt` (PostgreSQL 17, Frankfurt region)
- Database name: `wave_transakt_staging`
- Database role: `wave_transakt`

The Neon project is a new, empty staging database. Flyway is enabled; the API will attempt to apply its migrations at startup. Review the Koyeb build/runtime logs and verify the health endpoint before pointing any app build at it.

## Deploy the API to Koyeb

1. Sign in to Koyeb, then choose **Create Web Service** → **GitHub**. Authorize the Koyeb GitHub App for the private repository `Mams62/Wave_Transakt_API` if prompted.
2. Select branch `feat/koyeb-neon-staging-20261012`.
3. Select the **Dockerfile** builder and use the repository-root `Dockerfile`.
4. Configure the service to expose HTTP port `8080`, route `/` to that port, set `PORT=8080`, and configure the health check path `/api/health`.
5. Add the environment variables below in Koyeb's service configuration. Use Koyeb's secret fields for every credential. Do not commit secrets to Git or paste them into chat.
6. Deploy. Review build logs, then runtime logs. A completed container build alone is not proof the application is healthy.

## Required environment variables

Use the pooled Neon endpoint for normal application traffic. Copy the pooled endpoint host from the Neon Console's connection details. Do not include the database password in the JDBC URL; set it separately.

- `WAVE_DB_URL=jdbc:postgresql://<NEON_POOLED_HOST>/wave_transakt_staging?sslmode=require`
- `WAVE_DB_USERNAME=wave_transakt` (or the exact role shown by Neon)
- `WAVE_DB_PASSWORD=<Neon role password>` (secret)
- `WAVE_JWT_SECRET=<fresh random secret, at least 32 random bytes>` (secret)
- `WAVE_JWT_EXPIRATION=86400000`
- `PORT=8080`
- `WAVE_SQL_LOGGING=false`
- `WAVE_LOG_LEVEL=INFO`
- `WAVE_RETURN_VERIFICATION_CODE=false`
- `WAVE_LEGACY_PAYSTACK_ENABLED=false`
- `WAVE_LOCAL_MONEY_MOVEMENT_ENABLED=false`
- `WAVE_SERVICE_PAYMENTS_ENABLED=false`
- `WAVE_INTERSWITCH_ENABLED=false`
- `WAVE_INTERSWITCH_TRANSFER_ENABLED=false`
- `WAVE_INTERSWITCH_TRANSFER_PRODUCT_APPROVED=false`
- `WAVE_EXTERNAL_BANK_TRANSFER_EXECUTION_ENABLED=false`
- `WAVE_LIVENESS_PROVIDER_CONFIGURED=false` until valid identity-provider credentials are installed as Koyeb secrets.

Do not set any verification-code shortcut or enable real money movement to make a demo pass. OTP delivery and live identity verification will only work when the proper providers and credentials are configured; otherwise those flows should remain unavailable/fail closed.

Important: the application entrypoint builds a JDBC URL from `WAVE_DB_HOST` when that variable is set. For Neon, set `WAVE_DB_URL` exactly as above and leave `WAVE_DB_HOST` unset so that `sslmode=require` is preserved.

## Verify after deployment

1. Confirm the deployment is healthy and the Koyeb health check succeeds at `/api/health`.
2. Check the logs for Flyway migration success and confirm there are no database DNS, TLS, authentication, or schema-validation errors.
3. Test registration, login, OTP delivery, identity verification, and transfer-readiness endpoints only with authorized test data and provider sandbox credentials.
4. Keep external bank-transfer execution and all live movement of funds disabled unless separately approved and supported by the bank/provider.
5. Only after the API is verified, set the Android build's API base URL to the new Koyeb HTTPS URL, build a fresh APK, install it, and test again.

## Limits / rollback

The Koyeb free instance is a resource-constrained test target, not a production SLA. It may be slow during startup or run out of memory; do not interpret one successful health check as production readiness. Preserve the original Render resources until the replacement is tested and data recovery is separately addressed. This setup does not copy old Render data to Neon.
