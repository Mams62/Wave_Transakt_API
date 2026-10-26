# Wave Transakt — secure staging identity-provider activation

Date: 2026-10-26. **Staging only; no payment, customer funds or production identity activation.**

## Confirmed failure and work completed

- Railway staging A, `wave-transakt-api-staging-staging-e587.up.railway.app`, received multiple `POST /api/auth/register` requests returning HTTP 503. Spring Boot itself and PostgreSQL were healthy.
- `AuthService.register` requires **two verified government lookups (BVN + NIN)** before saving a User or issuing a wallet. The default integration is Dojah.
- The selected Railway service has neither `WAVE_DOJAH_APP_ID` nor `WAVE_DOJAH_API_KEY`, so `DojahGovernmentIdentityClient` rejects registration; this is an intentional security boundary, not a server timeout. No synthetic identity or bypass should be used as a workaround.
- Backend PR #55, pinned commit `96b0975d692331c8856f8969f2429a2e8539e069`, was deployed to Railway A. The new public `GET /api/auth/registration/availability` reports only whether government-identity provider credentials exist, with `Cache-Control: no-store`. It never receives identity numbers, claims an identity was verified, or reveals credentials.
- Backend CI passed, SQL-backed `/api/health/ready` returned 200 READY, and anonymous `/api/auth/me` and `/api/wallet` returned 401.
- Android PR #38 adds a no-PII preflight before BVN/NIN entry, disables submission while provider readiness cannot be established, and preserves the explicit secure registration/login paths.
- Railway A `WAVE_DOJAH_BASE_URL` was set to `https://sandbox.dojah.io` rather than the live billable endpoint. Liveness remains unavailable until authorized.
- Existing isolated Neon staging snapshot and separate Render original-data recovery work are untouched.

## Provider onboarding — an authorized Dojah account is required

1. Sign in to the **existing authorized Dojah account**. Official instructions: [Dojah BVN/NIN integration](https://dojah.io/blog/bvn-nin-verification-nigeria).
2. Navigate to **Developers > Configuration > My Apps**; create a Wave Transakt app if there isn't one. Retrieve its **App ID** and **Secret Key**.
3. Ensure the Dojah account is approved/enabled for the **BVN + NIN verification products**. Keys existing does not prove product entitlement or API readiness.
4. In the **Railway staging A service's Variables settings**, add `WAVE_DOJAH_APP_ID` and `WAVE_DOJAH_API_KEY` as protected/sealed variables where supported. Do not paste any secret, BVN, NIN, identity image, or verification code into ChatGPT, GitHub commits, screenshots or support logs.
5. Keep `WAVE_DOJAH_BASE_URL=https://sandbox.dojah.io` for staged, non-billable contract testing. Do not switch to `https://api.dojah.io` without authorization, approved provider products, billing review, consent requirements and verification safeguards.
6. After an approved staging redeployment, the registration availability endpoint can confirm **configuration presence only**. Test actual provider authorizations and identity matching with **provider-approved, consented test records**, not arbitrary or other people's identifying information.
7. Configure a legitimate **outbound email-code delivery provider**; the current API creates hashed email verification codes, but no delivery service has been verified in staging. Never expose OTP codes in API responses.
8. Configure the approved SMS provider before live phone OTP login. The selected Railway staging service currently lacks `WAVE_SMS_API_KEY` and `WAVE_SMS_BASE_URL`.
9. Keep `WAVE_RETURN_VERIFICATION_CODE=false` and all Wema/interswitch/local-money-movement switches disabled. Verify liveness and selfie-to-government-ID matching only after Dojah liveness product activation. No bypass for Face ID, BVN, NIN, email or OTP.

## How to validate without financial or identity writes

- `GET /api/health/ready` -> 200 READY only if PostgreSQL answers `SELECT 1`.
- `GET /api/auth/registration/availability` -> 200 with `registrationSubmissionAllowed:false` until credentials are present (it is not proof of verification).
- Anonymous `GET /api/auth/me` and `GET /api/wallet` -> 401/403.
- Real registration must never report a verified account or confirmed wallet when Dojah or the email provider is unavailable.
- Release gates: tests, sanctioned sandbox contract check, proper email and SMS delivery, biometric identity challenge, secure bank-settlement restrictions, and real-device visual QA.

## Operational caution

Do not migrate, rename or delete the original Render database while data recovery is unverified; its Free instance expires 2026-11-23. Keep Railway B as fallback. Backend rollback remains `d237a57840765611c38b70be2641244e17b4928a` until the new build is proven stable.
