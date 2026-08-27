# Wave Transakt API

Spring Boot backend for Wave Transakt wallet services and Paystack wallet funding.

## Requirements

- Java 17+
- Maven 3.9+
- PostgreSQL
- Paystack test or live secret key

## Local database

Create a PostgreSQL database named `wave_transakt`.

The application uses these environment variables:

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/wave_transakt
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password
PAYSTACK_SECRET_KEY=sk_test_xxxxxxxxx
```

Never commit a real Paystack secret key to GitHub.

## Start

```bash
mvn spring-boot:run
```

The API runs on `http://127.0.0.1:8080` by default.

## Development user

Until JWT authentication is connected, create a development user:

```http
POST /api/v1/users
Content-Type: application/json

{
  "email": "customer@example.com",
  "fullName": "Wave Test User"
}
```

Save the returned `id`.

## Initialize wallet funding

```http
POST /api/v1/wallet/funding/initialize
X-User-Id: <USER_UUID>
Content-Type: application/json

{
  "amount": 5000.00
}
```

The response contains a Paystack authorization URL and transaction reference.

## Verify funding

After Paystack reports a successful payment, verify it through:

```http
GET /api/v1/wallet/funding/verify/<REFERENCE>
X-User-Id: <USER_UUID>
```

The backend checks the Paystack status and amount before crediting the wallet. Successful verification is idempotent, so the same reference cannot credit the wallet twice.

## Important production step

`X-User-Id` is intentionally temporary. Replace it with the authenticated user's ID from Wave Transakt JWT/Spring Security authentication before production use. Do not allow clients to choose another user's UUID.
