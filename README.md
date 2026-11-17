# PayGrid — Secure Transaction Management System

PayGrid is an enterprise-grade backend system for secure account management, role-based authorization, and high-integrity financial transactions built with **Java 21**, **Spring Boot 3**, and **PostgreSQL**.

---

## 1. Architecture Overview

PayGrid follows a clean, decoupled **Layered Architecture**:

```
[ Client Request ]
       │
       ▼
[ Security Filter Chain (JwtAuthenticationFilter) ]
       │
       ▼
[ Controller Layer (@RestController) ] ── (Validates Request DTOs)
       │
       ▼
[ Service Layer (@Service) ] ─────────── (Business Logic, Idempotency, Transactions)
       │
       ▼
[ Repository Layer (@Repository) ] ───── (Spring Data JPA / Hibernate)
       │
       ▼
[ PostgreSQL / H2 Database ] ─────────── (Flyway Versioned Migrations)
```

### Core Architecture Principles:
- **Separation of Concerns**: Controllers handle HTTP protocol concerns and request validation; Services encapsulate business logic and atomic boundaries (`@Transactional`); Repositories manage database access.
- **DTO Encapsulation**: Domain JPA entities (`User`, `Transaction`) are strictly internal and never exposed directly over REST APIs.
- **Stateless Authentication**: JWT tokens signed using HMAC-SHA256 authenticate each incoming request via Spring Security filters.
- **Idempotency Guarantee**: Financial requests can include an `idempotencyKey` to prevent double-charging or duplicate transfers during network retries.
- **Strict Data Integrity**: All monetary values use `java.math.BigDecimal` with 4 decimal digits of precision (`NUMERIC(19, 4)` in SQL) to prevent floating-point rounding errors.

---

## 2. Technology Stack

| Technology | Purpose |
|---|---|
| **Java 21** | Modern LTS Java runtime with sealed classes, pattern matching, and performance enhancements |
| **Spring Boot 3.2.5** | Core application framework |
| **Spring Security 6** | Role-based authorization and stateless filter chain |
| **JWT (JJWT 0.12.5)** | Secure token-based authentication (HMAC-SHA256) |
| **BCrypt** | Salted one-way password hashing |
| **Spring Data JPA / Hibernate 6** | Object-Relational Mapping (ORM) and dynamic specifications |
| **Flyway 9.22** | Automated, version-controlled database migrations |
| **PostgreSQL** | Primary production relational database |
| **H2 Database** | Fast in-memory database with PostgreSQL compatibility mode for testing |
| **Jakarta Bean Validation** | Declarative request payload validation (`@NotBlank`, `@DecimalMin`, `@Size`, `@Email`) |
| **Springdoc OpenAPI 3 / Swagger** | Interactive API documentation and schema exploration |
| **JUnit 5 & Mockito** | Comprehensive unit, service, and MockMvc integration tests |

---

## 3. Package Structure

```
com.paygrid
├── PayGridApplication.java
├── config
│   ├── ApplicationConfig.java          # Common beans (PasswordEncoder)
│   ├── OpenApiConfig.java              # Swagger 3 & OpenAPI definition with BearerAuth
│   └── SecurityConfig.java             # Spring Security filter chain and endpoint rules
├── controller
│   ├── AuthController.java             # /api/v1/auth (register, login, me)
│   ├── TransactionController.java      # /api/v1/transactions (deposit, transfer, withdraw, history)
│   └── UserController.java             # /api/v1/users (profile, lookup)
├── dto
│   ├── request
│   │   ├── DepositRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── TransferRequest.java
│   │   ├── UpdateTransactionStatusRequest.java
│   │   └── WithdrawalRequest.java
│   └── response
│       ├── AuthResponse.java
│       ├── ErrorResponse.java
│       ├── TransactionResponse.java
│       └── UserResponse.java
├── entity
│   ├── Role.java                       # ROLE_USER, ROLE_ADMIN
│   ├── Transaction.java                # JPA entity mapping to 'transactions' table
│   ├── TransactionStatus.java          # PENDING, PROCESSING, SUCCESS, FAILED
│   ├── TransactionType.java            # TRANSFER, DEPOSIT, WITHDRAWAL
│   └── User.java                       # JPA entity mapping to 'users' table
├── exception
│   ├── DuplicateTransactionException.java
│   ├── GlobalExceptionHandler.java     # Centralized @RestControllerAdvice
│   ├── InsufficientBalanceException.java
│   ├── InvalidCredentialsException.java
│   ├── InvalidTransactionException.java
│   ├── PayGridException.java
│   ├── ResourceNotFoundException.java
│   └── UnauthorizedAccessException.java
├── repository
│   ├── TransactionRepository.java      # JpaRepository + JpaSpecificationExecutor
│   └── UserRepository.java             # JpaRepository for user persistence
├── security
│   ├── CustomAccessDeniedHandler.java  # Custom 403 Forbidden handler
│   ├── CustomUserDetails.java          # UserDetails implementation
│   ├── CustomUserDetailsService.java   # Loads users by username or email
│   ├── JwtAuthenticationEntryPoint.java# Custom 401 Unauthorized handler
│   ├── JwtAuthenticationFilter.java    # Intercepts requests & validates Bearer tokens
│   └── JwtTokenProvider.java           # Generates, signs, and parses JWT tokens
└── service
    ├── AuthService.java
    ├── TransactionService.java
    ├── UserService.java
    └── impl
        ├── AuthServiceImpl.java
        ├── TransactionServiceImpl.java
        └── UserServiceImpl.java
```

---

## 4. Database Schema (Flyway V1)

Versioned migration script: `src/main/resources/db/migration/V1__init_schema.sql`

### `users` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PRIMARY KEY, IDENTITY | Unique user identifier |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | User login handle |
| `email` | VARCHAR(100) | NOT NULL, UNIQUE | User email address |
| `password` | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| `role` | VARCHAR(20) | NOT NULL | `ROLE_USER` or `ROLE_ADMIN` |
| `account_number` | VARCHAR(36) | NOT NULL, UNIQUE | Unique account number (`PG-XXXXXXXX`) |
| `balance` | NUMERIC(19, 4) | NOT NULL, DEFAULT 0 | Account ledger balance |
| `created_at` | TIMESTAMP | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

### `transactions` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PRIMARY KEY, IDENTITY | Internal transaction ID |
| `reference_id` | VARCHAR(64) | NOT NULL, UNIQUE | Public reference (`TXN-XXXXXXXXXXXXXXXX`) |
| `idempotency_key` | VARCHAR(128) | UNIQUE | Client-supplied key to prevent duplicates |
| `source_user_id` | BIGINT | FK -> users(id) | Source user (null for deposits) |
| `target_user_id` | BIGINT | FK -> users(id) | Target user (null for withdrawals) |
| `type` | VARCHAR(20) | NOT NULL | `TRANSFER`, `DEPOSIT`, `WITHDRAWAL` |
| `amount` | NUMERIC(19, 4) | NOT NULL | Transaction principal amount |
| `fee` | NUMERIC(19, 4) | NOT NULL, DEFAULT 0 | Processing fee |
| `currency` | VARCHAR(3) | NOT NULL, DEFAULT 'USD' | 3-letter currency code |
| `status` | VARCHAR(20) | NOT NULL | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `description` | VARCHAR(255) | | Optional transaction memo |
| `failure_reason` | VARCHAR(255) | | Reason message if status is FAILED |
| `created_at` | TIMESTAMP | NOT NULL | Timestamp of transaction initiation |
| `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## 5. API Reference

### 5.1 Authentication Endpoints (`/api/v1/auth`)

#### Register User
`POST /api/v1/auth/register`
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Password123!",
  "role": "ROLE_USER",
  "initialDeposit": 1000.00
}
```
**Response (`201 Created`):**
```json
{
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "ROLE_USER",
  "accountNumber": "PG-A1B2C3D4",
  "balance": 1000.0000,
  "createdAt": "2026-11-17T03:00:00"
}
```

#### Login
`POST /api/v1/auth/login`
```json
{
  "username": "alice",
  "password": "Password123!"
}
```
**Response (`200 OK`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "role": "ROLE_USER",
    "accountNumber": "PG-A1B2C3D4",
    "balance": 1000.0000
  }
}
```

#### Get Current Authenticated Profile
`GET /api/v1/auth/me`  
**Header:** `Authorization: Bearer <TOKEN>`  
**Response (`200 OK`):** Returns caller's profile.

---

### 5.2 Transaction Endpoints (`/api/v1/transactions`)
*(All transaction endpoints require `Authorization: Bearer <TOKEN>`)*

#### Deposit Funds
`POST /api/v1/transactions/deposit`
```json
{
  "amount": 250.00,
  "description": "Checking account deposit",
  "idempotencyKey": "dep-8823192"
}
```

#### Transfer Funds
`POST /api/v1/transactions/transfer`
```json
{
  "targetAccountNumber": "PG-B9C8D7E6",
  "amount": 150.00,
  "description": "Project milestone payment",
  "idempotencyKey": "tx-client-uuid-9921"
}
```

#### Withdraw Funds
`POST /api/v1/transactions/withdraw`
```json
{
  "amount": 50.00,
  "description": "ATM withdrawal",
  "idempotencyKey": "with-44123"
}
```

#### Get Transaction by Reference ID
`GET /api/v1/transactions/{referenceId}`  
*Note: Users can only retrieve transactions they own (as sender or receiver). Administrators can view any transaction.*

#### Get Transaction History (Paginated & Filtered)
`GET /api/v1/transactions?status=SUCCESS&type=TRANSFER&page=0&size=10&sortBy=createdAt&direction=desc`

#### Admin: Update Transaction Status
`PATCH /api/v1/transactions/{referenceId}/status` *(Requires `ROLE_ADMIN`)*
```json
{
  "status": "FAILED",
  "failureReason": "Suspicious activity flagged by risk engine"
}
```

---

## 6. How to Run Locally

### Prerequisites
- Java 21+ (`java -version`)
- Maven 3.8+ (included via `./mvnw`)

### Option A: Running with In-Memory PostgreSQL-Compatible H2 (Default Dev Profile)
The project comes preconfigured with a development profile (`dev`) that runs against an in-memory database with PostgreSQL syntax and Flyway migrations enabled out-of-the-box:

```bash
./mvnw spring-boot:run
```
The server will start on port `8080`.

### Option B: Running with Native PostgreSQL
1. Start PostgreSQL and create the database:
```sql
CREATE DATABASE paygrid;
```
2. Set environment variables (or create a `.env` file from `.env.example`):
```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/paygrid
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
```
3. Run the application:
```bash
./mvnw spring-boot:run
```

---

## 7. Interactive Swagger / OpenAPI Documentation

Once the application is running, open your browser to:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

You can click **Authorize** in Swagger UI and paste your JWT token as `Bearer <token>` to test all secured endpoints interactively.

---

## 8. Running Automated Tests

Run the full automated test suite containing 27 unit, service, and integration tests:

```bash
./mvnw test
```

### Test Coverage Highlights:
- **`UserServiceTest`**: Unit testing user creation, duplicate username/email detection, and BCrypt hashing.
- **`TransactionServiceTest`**: Unit testing atomic balance adjustments, insufficient funds validation, self-transfer prevention, idempotency key deduplication, and ownership security checks.
- **`JwtTokenProviderTest`**: Unit testing token generation, parsing of custom claims, signature verification, and expired token rejection.
- **`AuthControllerIntegrationTest`**: End-to-end HTTP API tests for user registration, input validation errors, login, and secured endpoint authorization via `MockMvc`.
- **`TransactionControllerIntegrationTest`**: End-to-end tests for fund transfers, deposits, withdrawals, paginated transaction histories, and ownership isolation (403 Forbidden).

