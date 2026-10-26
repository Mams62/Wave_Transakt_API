# Wave Transakt — company domain, email and Prembly activation

Checked: 26 October 2026. **No domain has been purchased and no mailbox has been created.**

## Domain selection
- Preferred: `wavetransakt.com` (available at last registrar check).
- Vercel domain search showed an indicative 1-year registration price of USD 11.25 and same stated renewal price, before any applicable taxes/fees; availability and price can change.
- `wavetransakt.ng` and `wavetransakt.com.ng` were unavailable when checked.
- The connected Vercel account listed no teams and no registered domains. Domain purchase requires creating/connecting an authorized Vercel team, an explicit final-quote approval and full legal registrant contact details. **Do not make a non-refundable purchase on a presumed price or invent registrant information.**

## Recommended company mailbox
- Intended first mailbox (NOT ACTIVE): `admin@wavetransakt.com`
- Future role aliases after verified setup: `compliance@wavetransakt.com`, `support@wavetransakt.com`.
- Preferred minimal-cost mail provider: Zoho Mail. Its free plan can host one custom domain for up to five users (5 GB each, webmail only), depending on data-center/region availability. Paid Mail Lite is another option.
- Do not configure provider MX records until the corresponding mailbox accounts actually exist.
- After business-domain purchase, add/verify domain in the mail provider, then enter only provider-issued DNS TXT/MX/SPF/DKIM records and a deliberate DMARC policy in the DNS zone. Avoid guessed records and do not disrupt the API's DNS.
- Test delivery to/from an independent address, check SPF/DKIM/DMARC and restrict administrator access to approved company representatives.

## Prembly activation
- Signup: https://app.prembly.com/signup (requires company email). An actual mailbox and inbox ownership must be confirmed before signup.
- Company: WAVE TRANSAKT LTD, RC 9915202. Business documents/director information must be submitted only by an authorized representative, through Prembly's secure dashboard.
- Prembly inquiry to `team@prembly.com` is drafted in the company's connected Gmail; it was NOT SENT.
- The backend Prembly identity adapter lives in this PR. Its BVN endpoint is `/verification/bvn_validation`, NIN advanced endpoint is `/verification/vnin`, and both require a provider-issued `x-api-key`. The adapter never ships these credentials to Android.
- The environment defaults to `WAVE_GOVERNMENT_IDENTITY_PROVIDER=DISABLED`; `WAVE_PREMBLY_API_KEY` must be set ONLY on the approved backend environment after signup and entitlement verification.
- **Registration must remain disabled** until permitted BVN/NIN lookups, consent, email/SMS OTP and a compatible provider-approved liveness/face-matching workflow have been tested together. A successful identity-number lookup is not sufficient to activate a wallet.
- Do not expose NIN, BVN, customer portraits, API keys, OTPs or provider response bodies in logs, pull requests, or correspondence.

## Explicit operator checklist
1. Approve full domain registration cost and auto-renew setting.
2. Supply a legally authorized registrant contact and associate a payment-capable company registrar account.
3. Register the domain and complete registrar contact verification.
4. Activate and verify the custom-domain mailbox using the mail provider's instructions.
5. Complete Prembly company signup and compliance review.
6. Add sandbox credentials privately to isolated staging; test BVN/NIN/error responses and independent liveness.
7. Keep financial features and registration off until the formal go-live gates pass.

This file records the setup plan, not an assertion that external account creation or identity verification has been completed.
