# Wave Transakt AWS staging foundation

This directory defines the target AWS staging architecture for the Wave Transakt API. Render remains the rollback environment until AWS staging passes migration, correctness, latency and load tests.

## Target architecture

- Application Load Balancer -> ECS Fargate Spring Boot tasks in private subnets.
- Amazon RDS for PostgreSQL in private database subnets. Production must use Multi-AZ; staging may use a smaller topology while preserving the same migration path.
- AWS Secrets Manager for database credentials, JWT secrets and provider credentials. No provider secret belongs in task definitions, GitHub, Android or POS source.
- CloudWatch Logs/metrics/alarms and ALB health checks against `/api/health`.
- AWS WAF on the public entry point before production cutover.
- NAT/private egress for provider APIs. Provider callbacks terminate at the public load balancer and are verified by application-level signatures/secrets where supported.

## Financial safety invariants

1. `WAVE_EXTERNAL_BANK_TRANSFER_EXECUTION_ENABLED=false` by default.
2. Provider acquiring/card/contactless flags remain false until explicit approval.
3. Flyway remains the only schema migration mechanism; Hibernate is `validate`, never schema-create/update.
4. Database migration is performed from a verified backup/copy before any traffic cutover.
5. No DNS cutover until idempotency, concurrent wallet reservation, reversal, pending/requery and ledger-balance tests pass against AWS staging.
6. Render is not removed during the initial migration.

## Required runtime configuration

The application already accepts `WAVE_DB_URL`, `WAVE_DB_USERNAME`, `WAVE_DB_PASSWORD`, `WAVE_JWT_SECRET`, provider-specific `WAVE_*` variables and `PORT`. AWS should inject secrets from Secrets Manager at runtime rather than storing secret values in IaC state or repository files.

## Deployment phases

1. Build immutable Java 21 container and push it to private ECR.
2. Provision isolated VPC, private application/database subnets, ALB, ECS service, RDS PostgreSQL and logging.
3. Restore a staging database copy, then allow the application to validate/apply Flyway migrations.
4. Smoke-test `/api/health`, authentication, wallet reads and all fail-closed provider readiness endpoints.
5. Run transaction correctness/concurrency/load tests. Keep real external transfer execution disabled.
6. Point an Android staging build at the AWS staging API only after backend tests pass.
7. Create a separate production environment; never reuse staging secrets or database.
8. Perform controlled DNS cutover with rollback to the old environment available.

## Region selection

Do not hardcode a production AWS region based on geography alone. Benchmark candidate AWS regions from Nigerian client networks and against payment-provider endpoints, then select based on measured latency, service availability, compliance/data-residency requirements and operational resilience.
