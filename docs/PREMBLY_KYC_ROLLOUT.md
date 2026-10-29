# Prembly KYC integration — controlled rollout

Status: FOUNDATION ONLY. This branch is not ready to merge or deploy.

## Safety blockers found on main
- AuthService.register saves a user and provisions a wallet before external KYC.
- VerificationService.verifyEmail sets AccountStatus.ACTIVE without KYC approval.
- Existing user records include raw BVN/NIN; assess encryption/tokenization and retention before migration.
- Controlled staging and newer draft PRs must be reconciled before changing registration.

## Implementation gates
1. Confirm current Prembly sandbox contract, host, auth headers, BVN/NIN endpoints and approved biometric workflow. Documentation versions differ.
2. Acquire approved sandbox access; store credentials in Railway/Render secrets only.
3. Add explicit consent and server-side identity matching; treat PENDING/UNAVAILABLE as non-approved.
4. Implement persistent KYC state and audit references, encrypted/minimized personal data, and database migration.
5. Refactor registration and email verification so only approved KYC plus required checks can activate an account; wallet provisioning after approvals.
6. Test provider errors, timeouts, duplicates, retries, negative matches, and concurrency with authorized sandbox fixtures.
7. Review against active staging PRs, run Maven tests and CI, then deploy only to isolated staging after approval.
8. Keep money movement and service payments disabled. Do not expose keys or identity numbers in logs.

Prembly docs: https://developer.myidentitypass.com/
