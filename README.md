# Technix — online electronics shop · v1.0

Technix is a B2C e-commerce platform for **laptops and phones**. This is Project 1 (Foundation):
secure user accounts, the database, and a product catalog with browsing, filtering and search.

**Stack:**
- Spring Boot (Java 21)
- PostgreSQL 16
- Next.js (React + TypeScript)
- Mailpit (catches emails)

All of it runs in Docker.

## What you need

- **Docker Desktop** (or Docker Engine with Compose). Nothing else: Java, Node and the database all run inside containers.

## Start it

```bash
cd i-love-shopping1
cp .env.example .env          # Windows PowerShell: copy .env.example .env
docker compose up --build
```

The first build takes a few minutes. Then open:

| What                    | URL                     |
|-------------------------|-------------------------|
| Shop (frontend)         | http://localhost:3000   |
| API (backend)           | http://localhost:8080   |
| Mailpit (caught emails) | http://localhost:8025   |

- **Stop:** `Ctrl+C`, or run `docker compose down`.
- **Reset the database:** `docker compose down -v`.

The default `.env` works as it is. It uses Google's **reCAPTCHA test keys**, so the checkbox always passes, and a local-only `TOTP_ENCRYPTION_KEY` (the key that encrypts 2FA secrets, at least 32 characters; use your own random value outside local development). The only thing that needs your own key is the **"Continue with Google"** button: put a Google OAuth Client ID in `GOOGLE_CLIENT_ID` and `NEXT_PUBLIC_GOOGLE_CLIENT_ID`. Every setting is explained in [.env.example](.env.example).

> Already have a `.env` from an older version? Add `TOTP_ENCRYPTION_KEY` from `.env.example`, or the backend won't start.

> Ports 3000, 8080, 5432, 1025 and 8025 must be free.

## How to use it

1. **Browse:** search, filter and open products on the home page. Demo products are seeded on first start.
2. **Register** at `/register`: email, password, CAPTCHA. Name and address are optional.
3. **Log in** at `/login`, with your email or with Google. If 2FA is on, a second step asks for the 6-digit code.
4. **Forgot password:** use `/forgot-password`, open the email in **Mailpit** (http://localhost:8025) and click the link.
5. **My account** (`/account`):
   - **Profile tab:** edit your name and address.
   - **Security tab:**
     - **Change email:** enter the new address and your password. A confirmation link goes to the new address (see Mailpit) and a notice goes to the old one. The email changes only after you click the link.
     - **Change password:** this logs you out on every device.
     - **Two-factor authentication:** scan the QR code with Google Authenticator or Authy, then confirm with a code. Turning it off also needs a code.
     - **Delete account:** confirm with your password, or with Google for Google-only accounts.
6. **API console** (`/api`): call any endpoint with your access token attached.

### API

| Method  | Path               | Login needed | What it does                                                 |
|---------|--------------------|--------------|--------------------------------------------------------------|
| POST    | `/register`        | no           | Create an account                                            |
| POST    | `/login`           | no           | Log in with email and password (may return a 2FA challenge)  |
| POST    | `/googlelogin`     | no           | Log in with Google (may return a 2FA challenge)              |
| POST    | `/login/2fa`       | no           | Second login step: challenge + 6-digit code                  |
| POST    | `/refresh`         | cookie       | Get a new access token                                       |
| POST    | `/logout`          | no           | Revoke your tokens                                           |
| POST    | `/forgot-password` | no           | Send a reset email                                           |
| POST    | `/reset-password`  | no           | Set a new password                                           |
| POST    | `/confirm-email`   | no           | Confirm an email change with the token from the link        |
| GET/PUT | `/me`              | yes          | Read or update your profile                                  |
| DELETE  | `/me`              | yes          | Delete your account (needs password or a fresh Google login) |
| POST    | `/me/password`     | yes          | Change password (ends every session)                         |
| POST    | `/me/email`        | yes          | Request an email change (sends the confirmation link)        |
| POST    | `/me/2fa/setup`    | yes          | Start 2FA setup: returns the secret and the QR code URI      |
| POST    | `/me/2fa/enable`   | yes          | Turn 2FA on with a code from the app                         |
| POST    | `/me/2fa/disable`  | yes          | Turn 2FA off with a code from the app                        |
| GET     | `/categories`      | no           | Category tree                                                |
| GET     | `/search`          | no           | Search, filter, sort and page products                       |
| GET     | `/search/suggestions?q=` | no     | Up to 6 product names matching `q` (name or brand), for the search box |

Every other route requires a valid access token (deny by default).

Search example:
`/search?categoryId=1&minPrice=500&ram_gb=16&sort=price,asc&page=0&size=20`

## Features

**Accounts and security**
- Passwords are hashed with BCrypt.
- reCAPTCHA on registration, checked on the server.
- Google sign-in.
- The **access token** (JWT, 30 min) is kept only in memory.
- The **refresh token** (5 days) is in an HttpOnly cookie. It is rotated on every use, and reusing an old one revokes the whole session.
- Logout revokes both tokens.
- Password reset by email: single-use, expiring link. A reset logs out all sessions.
- Change password: needs the current password and logs out every device.
- Change email: needs the password. A single-use link (1 hour) goes to the new address and a notice to the old one.
- Optional **2FA (TOTP, RFC 6238)** with any authenticator app:
  - The secret is encrypted in the database (AES-GCM).
  - A code can't be used twice.
  - After 5 wrong codes there is a 5-minute lockout.
- Delete account (GDPR erasure): needs the password, or a fresh Google sign-in for Google-only accounts.
- Email links (reset, confirm email) are stored only as SHA-256 hashes.
- Validation in both the browser and the server, with clear error messages.

**Catalog**
- Category tree.
- Filters you can combine: brand, price, weight, CPU, GPU, RAM, display size.
- Sorting by price or rating, and pagination.
- Weight and size shown in metric and imperial.
- Search box with live suggestions (name or brand, from 2 characters), usable with the keyboard and screen readers.

**Extra:** cart drawer and "recently viewed" list (in the browser only), plus the `/api` console.

## Database

The ERD (entities, keys, cardinality, modality) is in **[ERD.md](ERD.md)**. Flyway migrations in `backend/src/main/resources/db/migration` create the schema on startup.

## Architecture

**Monolith** with clear layers:

`controller` (HTTP) → `service` (logic + transactions) → `repository` (database)

DTOs form the API contract, and entities are never sent to the client. One app and one database keep this simple to run, test and review. The layers still allow splitting out parts later if they need to scale.

## Tests

```bash
cd backend
./mvnw test        # Windows: mvnw.cmd test   (needs Java 21 + Docker running)
```

There are 176 automated tests:
- **Unit:** JWT, validation, services
- **API integration:** against a real Postgres via Testcontainers
- **Security:** SQL injection, XSS, malformed input, auth

The details are in [tests.md](tests.md).

## Not done yet

- 2FA recovery codes: if you lose your authenticator app, you can't log in until an admin turns 2FA off in the database
- The 2FA login challenges, failed-code counters and the access-token denylist are kept in memory. That is fine for one backend instance; running several would need a shared store such as Redis.
- Relevance sorting (left out on purpose: results are sorted by price, rating or weight)
- Rating filter
- Product image upload (images are seeded URLs)
- API docs (OpenAPI)

## Disclaimer

This project was built with the help of an AI assistant (Claude by Anthropic), used as a tutor and pair programmer. It explained concepts, reviewed code and wrote parts of the implementation. **All design and architecture decisions were made by me, Lauri Musto**, and I am responsible for the final result.
