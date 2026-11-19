# Technix — online electronics shop

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
git clone <repo-url>
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

The default `.env` works as it is. It uses Google's **reCAPTCHA test keys**, so the checkbox always passes. The only thing that needs your own key is the **"Continue with Google"** button: put a Google OAuth Client ID in `GOOGLE_CLIENT_ID` and `NEXT_PUBLIC_GOOGLE_CLIENT_ID`. Every setting is explained in [.env.example](.env.example).

> Ports 3000, 8080, 5432, 1025 and 8025 must be free.

## How to use it

1. **Browse:** search, filter and open products on the home page. Demo products are seeded on first start.
2. **Register** at `/register`: email, password, CAPTCHA. Name and address are optional.
3. **Log in** at `/login`, with your email or with Google.
4. **Forgot password:** use `/forgot-password`, open the email in **Mailpit** (http://localhost:8025) and click the link.
5. **My account** (`/account`): edit your profile. The Security tab lists planned features (change password, 2FA, change email, delete account), which are not built yet.
6. **API console** (`/api`): call any endpoint with your access token attached.

### API

| Method  | Path               | Login needed | What it does                           |
|---------|--------------------|--------------|----------------------------------------|
| POST    | `/register`        | no           | Create an account                      |
| POST    | `/login`           | no           | Log in with email and password         |
| POST    | `/googlelogin`     | no           | Log in with Google                     |
| POST    | `/refresh`         | cookie       | Get a new access token                 |
| POST    | `/logout`          | no           | Revoke your tokens                     |
| POST    | `/forgot-password` | no           | Send a reset email                     |
| POST    | `/reset-password`  | no           | Set a new password                     |
| GET/PUT | `/me`              | yes          | Read or update your profile            |
| GET     | `/categories`      | no           | Category tree                          |
| GET     | `/search`          | no           | Search, filter, sort and page products |

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
- Validation in both the browser and the server, with clear error messages.

**Catalog**
- Category tree.
- Filters you can combine: brand, price, weight, CPU, GPU, RAM, display size.
- Sorting by price or rating, and pagination.
- Weight and size shown in metric and imperial.
- Live search box.

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

There are 101 automated tests:
- **Unit:** JWT, validation, services
- **API integration:** against a real Postgres via Testcontainers
- **Security:** SQL injection, XSS, malformed input, auth

The details are in [tests.md](tests.md).

## Not done yet

- Two-factor authentication (TOTP), plus the other Security-tab features
- Search suggestions and relevance sorting
- Rating filter
- Product image upload (images are seeded URLs)
- API docs (OpenAPI)

## Disclaimer

This project was built with the help of an AI assistant (Claude by Anthropic), used as a tutor and pair programmer. It explained concepts, reviewed code and wrote parts of the implementation. **All design and architecture decisions were made by me, Lauri Musto**, and I am responsible for the final result.
