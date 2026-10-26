# Wave Transakt — staging identity, email, phone OTP, and biometrics

**Environment:** Existing Railway staging A + isolated Neon. **Not production.**

## Current verified gaps

- Railway staging A has `WAVE_DOJAH_BASE_URL`, but not the authorized
  `WAVE_DOJAH_APP_ID` or `WAVE_DOJAH_API_KEY`.
- `WAVE_SMS_BASE_URL` exists, but `WAVE_SMS_API_KEY` is missing.
- No verified outgoing email sender is configured.
- Liveness must be explicitly enabled only after authorized Dojah facial
  liveness and selfie-to-NIN verification are tested.
- Do not attempt to import or simulate BVN/NIN, face, customer, or ledger data
  from the original Render database. Preserve the source database and backups.
- The existing Wema / Interswitch financial movement switches remain OFF.

## Provider configuration (set directly in Railway Variables)

| Purpose | Required variable | Requirement |
| --- | --- | --- |
| Government KYC | `WAVE_DOJAH_APP_ID` | Actual approved Dojah app ID |
| Government KYC | `WAVE_DOJAH_API_KEY` | Secret from the provider console |
| KYC endpoint | `WAVE_DOJAH_BASE_URL` | `https://sandbox.dojah.io` for staging |
| Face verification | `WAVE_LIVENESS_PROVIDER_CONFIGURED` | Only `true` when liveness API product is approved, available and verified |
| SMS | `WAVE_SMS_BASE_URL` | Provider's authorized API base |
| SMS | `WAVE_SMS_API_KEY` | Valid Termii-compatible credentials |
| SMS | `WAVE_SMS_SENDER_ID` | Approved sender identity, not a made-up bank sender |
| Email SMTP (PR #58 option) | `WAVE_EMAIL_SMTP_HOST` | Approved SMTP host |
| Email SMTP | `WAVE_EMAIL_SMTP_PORT` | TLS STARTTLS port, commonly 587 |
| Email SMTP | `WAVE_EMAIL_SMTP_USERNAME` | Authenticated sender account |
| Email SMTP | `WAVE_EMAIL_SMTP_PASSWORD` | Dedicated provider app password / credential |
| Email SMTP | `WAVE_EMAIL_FROM` | Verified sender email |
| Email SMTP | `WAVE_EMAIL_SMTP_STARTTLS` | Keep `true`; plaintext SMTP disabled |

**Important:** PR #57 contains a *different*, already-tested Resend HTTPS
email-delivery implementation using `WAVE_EMAIL_API_KEY` and `WAVE_EMAIL_FROM`.
PR #58 is an SMTP alternative. **Choose one and deploy exactly one**;
don't combine their env assumptions or merge both directly.

## Required staged acceptance sequence

1. Confirm provider account ownership and approved KYC products in Dojah.
2. Set required secret values through Railway variable UI without posting keys
   into chats, pull requests, Android, logs, or support tickets.
3. Verify configured identity lookup, email, SMS, and liveness readiness with
   `GET /api/auth/registration/availability`. This checks configuration only,
   never substitutes for a successful real verification.
4. In a provider-approved sandbox, register with **consented test identity
   records** only. Ensure a verification message actually arrives in the inbox.
5. Verify the email code, then test PIN login, SMS OTP received at the registered
   number, live selfie, government-ID matching, and finally server JWT issuance.
6. Test fingerprint / device Face Unlock only **after full authentication**
   has created a valid, encrypted JWT. It is a local session-unlock gate, not
   a replacement for government identity or the server's face challenge.
7. Confirm rejected, expired, cancelled or unavailable biometric prompts never
   unlock a protected cached session.
8. Confirm bank transfers, wallet top-ups and unapproved payment flows remain
   restricted. Do not simulate financial success as a real transaction.

## Read-only checks

- `/api/health/ready`: PostgreSQL answers SELECT 1
- `/api/auth/registration/availability`: provider flags; never personal data
- `/api/auth/me` and `/api/wallet` anonymously: 401 or 403

**Never** turn on `WAVE_RETURN_VERIFICATION_CODE` or embed OTP
verification-code values into the Android app. No verification or payment
bypass is authorized.

## Deployment policy

This branch is a draft until CI passes and an email provider is selected.
Do not overwrite the original Render DB or push changes to the bank-facing
production branch merely because one staging service has an HTTP 200.
