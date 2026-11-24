# Render + Neon staging replacement

Prepared 23 November 2026; deployed and verified 24 November 2026.

Railway rejected a staging redeploy because its trial has expired. The existing
Java 21 / Spring Boot Docker application can instead be evaluated on a Render
Free web service using the existing Neon staging project. No paid plan is selected.

## Verified state

- Source: `fix/fail-closed-server-registration-provider-guard-20261026`, commit
  `accaf9fe9fc5c381e6e4ecfecb5989dfdb205feb`.
- Neon project: `wave-transakt-staging-alt` (`rapid-glitter-03986684`), PostgreSQL
  17, Frankfurt, in the personal Free organization.
- Existing branch: `main` (`br-twilight-shadow-b91zzlfl`). Read-only queries
  reconnected successfully after it had been archived/idle.
- Database: `wave_transakt_staging`, approximately 9.6 MB. It contains 32 public
  tables and 47 successful Flyway migration records through version 119. This
  is not an empty database. Migration checksums and application startup still
  require validation against the candidate deployment.
- The user confirmed `My Workspace` (`adaullah57@gmail.com`). Render's old free
  database `dpg-dbedsfnb9u1s739covv0-a` expired on 23 November and is suspended
  with a billing suspender; it was not deleted or upgraded by this work.
- The replacement uses the isolated Neon branch `render-staging-20261123`
  (`br-red-night-b9wotj2d`), leaving the original branch intact.

## Live verification — 24 November 2026

- Service: `srv-dc2l9pdg1s2s7396vf6g`, on the Free plan in Frankfurt.
- URL: https://wave-transakt-neon-staging-20261123-8onz.onrender.com
- Dashboard: https://dashboard.render.com/web/srv-dc2l9pdg1s2s7396vf6g
- Blueprint: `exs-dc2apl2fngtc73baepl0`; auto-sync and service auto-deploy are off.
- Deployment: `dep-dc2l9ptg1s2s7396vgd0`, status **live**, finished
  24 November at 10:15 WAT; deployed commit `faf68ebc00cd4aca0754513181f9b5f55e2a32db`.
- Flyway validated all 47 migrations, confirmed schema version 119 and applied
  no new migrations. JPA initialization and application startup succeeded.
- Initial process startup took approximately 153 seconds. Recorded memory use
  after startup was approximately 279 MiB within the 512 MiB limit; this is
  a short startup observation, not a load or endurance test.

| Request | Observed result |
| --- | --- |
| `GET /api/health` | HTTP 200, `UP` |
| `GET /api/health/ready` | HTTP 200, `READY` |
| `GET /api/auth/me` without credentials | HTTP 401, `UNAUTHORIZED` |
| `GET /api/auth/registration/availability` | HTTP 200; identity, email, SMS and face verification unconfigured; registration paused |

Registration and provider-backed account flows remain blocked on provider setup.
No real-money operation or identity submission was attempted. A new Android
staging preview is being built on `fix/render-neon-staging-20261124`; the offline
event-demo APK remains available independently.

The Neon project had used about 88.3 of its 100 monthly CU-hours when checked on
24 November. Remaining compute is limited until the 1 December reset; keep this
deployment intermittent and do not add keep-alive traffic. Full idle-resume and
authenticated account flows have not been verified.

## Deployment configuration

Use `render.neon-staging.yaml` as a **new, separate Blueprint** on
`chore/render-neon-staging-20261123`. Do not replace or synchronize the old
`render.yaml`: it references an older branch and a temporary Render database.
Confirm that `wave-transakt-neon-staging-20261123` is unused in the chosen
workspace before applying the new Blueprint, because Render matches resources
by name. The original services and databases remain available for investigation.

The service uses the existing Dockerfile, Frankfurt, port 8080 and the Free
compute plan. Automatic deploys are off. The 256 MB Java heap is a starting point
for the 512 MB container; native memory, startup time and actual requests must
be measured before calling the service healthy. Application code is unchanged.

The platform health check uses `/api/health`, which does not query PostgreSQL.
Deployment verification additionally checks `/api/health/ready`, which performs
`SELECT 1`. Repeated database probes can keep a free Neon compute awake, so do
not configure a frequent external keep-alive monitor as a workaround for sleep.

## Database and secrets — reproducible setup

1. Confirm the Render workspace, inspect existing resources and check remaining
   free usage. Do not upgrade plans, delete resources or attach a payment method.
2. Create an isolated branch of the verified Neon staging database within the
   existing project's free limits. Record its branch/endpoint IDs. Do not run
   startup migrations against the existing main branch as part of this trial.
3. Supply these values through Render's secret configuration during creation:

   | Variable | Value source |
   | --- | --- |
   | `WAVE_DB_URL` | `jdbc:postgresql://<new-direct-endpoint>/wave_transakt_staging?sslmode=require` |
   | `WAVE_DB_USERNAME` | The verified database owner/application role on that branch |
   | `WAVE_DB_PASSWORD` | That role's password from Neon; never place it in source or logs |
   | `WAVE_JWT_SECRET` | Generated by the new Render Blueprint |

   The same datasource serves Flyway and runtime traffic, so this initial
   deployment uses a direct endpoint with at most four Hikari connections.
   Use a separate direct migration datasource if pooled runtime connections are
   introduced later. Leave `WAVE_DB_HOST` unset so the entrypoint preserves TLS.
4. Keep OTP shortcuts and payment execution disabled. Identity-provider, SMS and
   email credentials must be configured separately before account flows can pass.
   Do not turn on provider-configured flags without verified sandbox credentials.

`sync: false` prompts for values only on initial Blueprint creation. Adding or
changing secrets on an existing service requires an explicit environment update.
Do not reuse a pre-existing environment group containing production credentials.

## Release checks — before further rollout

Before changing the normal Android app's API URL:

1. Validate the Blueprint against Render's current schema and platform validator.
   Local schema validation covers structure only; workspace/name/repository
   checks also passed during Render's successful first Blueprint plan.
2. Build and deploy the candidate. Verify Flyway validation/migrations and JPA
   schema validation complete without editing migration history or dropping data.
3. Confirm `/api/health` and `/api/health/ready` return successful responses,
   check memory and restart logs, and test a cold start after inactivity.
4. Check registration availability and authenticated test-account flows with
   authorized test data. Provider-unavailable responses must remain fail closed;
   server health alone does not establish working login, KYC or payments.
5. Only after those checks, build a separate staging Android APK using the new
   verified HTTPS API address. Preserve the existing app build and endpoint
   configuration until the candidate works.

The offline `Wave Transakt Demo` APK already supports event walkthroughs without
this deployment. It uses simulated transactions and cannot process real payments.

## Limits and recovery

Render Free currently provides 0.1 CPU and 512 MB RAM. It sleeps after 15 minutes
without inbound traffic and can take about one minute to wake. Free instance
hours are shared across the workspace (750 per month); build/bandwidth allowances
also apply. Check billing/spend settings before deployment, because bandwidth
overages can be charged when a payment method is present. Free Render Postgres
expires after 30 days, so this candidate uses Neon instead.

Neon Free currently includes 1 GB storage and 100 CU-hours per project per month,
with 5 GB public network transfer. Idle compute sleeps after five minutes. Quota
exhaustion can suspend access until the monthly reset. This is suitable for
intermittent staging; continuous production operation needs a separate capacity
and budget decision. This setup does not copy or recover old Render data.

If the candidate fails, keep the original resources and database main branch
untouched. Leave the Android API URL unchanged and use the offline event demo.
Do not delete the candidate branch as an automatic cleanup step.

## Current references

- [Render Free limits](https://render.com/docs/free.md)
- [Render compute plans](https://render.com/docs/compute-plans.md)
- [Blueprint specification](https://render.com/docs/blueprint-spec.md)
- [Blueprint JSON Schema](https://render.com/schema/render.yaml.json)
- [Neon pricing and quotas](https://neon.com/pricing)

Checked 23 November 2026. Recheck plan limits and account usage at deployment.
