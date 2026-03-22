# GovPay Backend

A fintech-grade digital wallet and government utility payment platform built with Spring Boot 3.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2, Java 17 |
| Security | Spring Security 6, JWT (jjwt 0.12) |
| Database | PostgreSQL 16 + Spring Data JPA |
| Migrations | Flyway |
| Notifications | HTTP integration with standalone notification service |
| Build | Maven |

## Architecture

```
com.govpay.govpay_backend
├── auth/           JWT auth, refresh token rotation, KYC status
├── wallet/         Balance, top-up, P2P transfers, transaction history
├── utility/        Bill generation, payment, mock government APIs
├── kyc/            Document submission, admin review workflow
├── admin/          Dashboard, user management, reports
├── notification/   HTTP client to notification service
└── common/         Exception handling, API response wrapper
```

## Getting Started

### Prerequisites
- Java 17
- PostgreSQL 16
- Maven

### 1. Create the database

```sql
CREATE DATABASE govpay;
```

### 2. Run migrations

Run the SQL files in order in pgAdmin:

```
V1__create_users_and_auth_tables.sql
V2__create_wallet_and_transaction_tables.sql
V3__create_utility_and_billing_tables.sql
V4__create_kyc_documents_table.sql
```

### 3. Configure application.yml

```yaml
spring:
  datasource:
    username: your_postgres_username
    password: your_postgres_password

govpay:
  notification:
    service-url: https://your-notification-service-url
```

### 4. Run

```bash
./mvnw spring-boot:run
```

API base URL: `http://localhost:8080/api`

## API Endpoints

### Auth
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | /auth/register | Public | Register new user |
| POST | /auth/login | Public | Login, get token pair |
| POST | /auth/refresh | Public | Rotate refresh token |
| POST | /auth/logout | Required | Revoke all tokens |
| GET | /auth/me | Required | Current user info |

### Wallet
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | /wallet | Required | Create wallet |
| GET | /wallet | Required | Get balance |
| POST | /wallet/top-up | Required | Add funds |
| POST | /wallet/transfer | Required | Send to another user |
| GET | /wallet/transactions | Required | Transaction history |

### Utility Billing
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | /utility/services | Required | List utility providers |
| POST | /utility/bills/generate | Required | Generate a bill |
| GET | /utility/bills | Required | My bills |
| POST | /utility/bills/{id}/pay | Required | Pay a bill |

### KYC
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | /kyc/submit | Required | Submit documents |
| GET | /kyc/status | Required | My KYC status |
| POST | /kyc/admin/{id}/approve | Admin | Approve KYC |
| POST | /kyc/admin/{id}/reject | Admin | Reject KYC |

### Admin
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | /admin/dashboard | Admin | Platform stats |
| GET | /admin/users | Admin | All users |
| PATCH | /admin/users/{id}/freeze | Admin | Freeze wallet |
| PATCH | /admin/users/{id}/suspend | Admin | Suspend user |
| GET | /admin/transactions | Admin | All transactions |
| GET | /admin/reports/transactions | Admin | Date-filtered report |

## Key Design Decisions

**Balance as BIGINT** — stored in smallest currency unit to avoid floating point errors. Division by 100 at presentation layer only.

**Refresh token rotation** — every refresh issues a new pair and revokes the old one. Reuse of a revoked token triggers full revocation across all user tokens.

**Deadlock prevention** — wallets always locked in consistent UUID order during transfers.

**Idempotency** — clients supply an idempotency key on transfers. Duplicate requests return the original response without re-executing.

**Optimistic locking** — `@Version` column on wallets prevents lost updates under concurrent transactions.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| DB_USERNAME | postgres | PostgreSQL username |
| DB_PASSWORD | — | PostgreSQL password |
| JWT_SECRET | (required in prod) | HMAC-SHA256 signing key |
| NOTIFICATION_SERVICE_URL | http://localhost:8081 | Notification service URL |
