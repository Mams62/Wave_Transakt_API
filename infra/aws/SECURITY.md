# AWS deployment security requirements

- ECS tasks and PostgreSQL must not have public IP addresses.
- RDS security group accepts PostgreSQL only from the ECS task security group.
- ECS task security group accepts application traffic only from the ALB security group.
- ALB accepts HTTPS; production HTTP must redirect to HTTPS.
- TLS certificate is managed through ACM.
- Secrets are referenced from Secrets Manager and are never committed as plaintext.
- IAM task roles follow least privilege. The execution role may pull the ECR image, write logs and read only the explicitly assigned runtime secrets.
- Enable encrypted RDS storage and encrypted automated backups. Production uses deletion protection and a deliberate backup retention policy.
- Enable ALB access logging/WAF logging in production without logging PINs, JWTs, authorization headers, provider credentials or fulfillment secrets.
- Use CloudWatch alarms for 5xx rate, unhealthy tasks, ALB target health, database CPU/connections/storage and application-level payment failure/pending anomalies.
- Production provider webhooks must be authenticated/verified according to each approved provider contract.
- Production cutover requires a tested database backup and rollback procedure.
