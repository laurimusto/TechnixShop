# Tests

Two layers of backend tests:

- **Unit tests** (JUnit 5 + Mockito + AssertJ) — every dependency mocked, no Docker/Postgres
  needed, fast, safe to run anywhere.
- **Integration tests** (`com.integration` package) — `@SpringBootTest` + `MockMvc` against a
  real, throwaway Postgres container via **Testcontainers**. These need Docker running
  locally; if Docker isn't reachable, they fail fast with a clear "Could not find a valid
  Docker environment" error rather than a confusing one.

## Running them

The Maven wrapper (`mvnw`) lives in `backend/`, not the repo root — `cd backend` first, or
every command below will fail with `No such file or directory`.

Run everything at once:

```
cd backend
./mvnw test          # Git Bash / macOS / Linux
mvnw.cmd test         # PowerShell / cmd
```

Run a single test class:

```
./mvnw test -Dtest=AuthServiceTest
```

Run a single test method:

```
./mvnw test -Dtest=AuthServiceTest#refresh_rotatesTokenWithinSameFamily
```

Run several specific classes at once:

```
./mvnw test -Dtest=AuthServiceTest,AccessTokenDenylistTest,CategoryServiceTest
```

## Seeing what passed/failed, and why

- **Console output**: `pom.xml` wires in the `maven-surefire-junit5-tree-reporter` plugin
  (ASCII theme, chosen because Unicode checkmarks didn't render reliably on Windows/Git Bash),
  so `./mvnw test` prints a tree — one line per test, showing its `@DisplayName` text with
  `[OK]` or `[XX]`, grouped by class, instead of just an aggregate "Tests run: X" count.
- A clean run ends with `BUILD SUCCESS`; any failure ends with `BUILD FAILURE` and prints the
  failing test's name plus a full stack trace / assertion diff directly in the console (AssertJ
  assertions show expected vs. actual values, e.g. `expected: "USER" but was: "ADMIN"`).
- **Every test has a `@DisplayName`** describing what it checks in plain English, so failures
  are readable without opening the source file.
- **Detailed per-test reports**: `backend/target/surefire-reports/` contains one `.txt` (and
  `.xml`) file per test class, listing every test method individually with PASS/FAIL and the
  full failure stack trace if any. Useful if a console scroll-back got cut off.

## What's covered

| Class | What it tests |
|---|---|
| `AuthServiceTest` | JWT generation (subject/role/`jti`/expiry), token parsing (tampered/expired rejection), refresh token rotation, reuse-detection (family-wide revocation), `createUser` validation (duplicate email, password mismatch, too-short password, too-long email), `login`/`authenticate` success and failure paths, `logout` (access-token denylist revocation + refresh-token DB revocation) |
| `AccessTokenDenylistTest` | Revoke/check semantics, including that a denylist entry whose stored expiry has already passed correctly reports "not revoked" even if Caffeine hasn't evicted it yet |
| `CategoryServiceTest` | Flat root categories, nested parent/child grouping, and the cycle-detection guard in `toTree` (exercised directly via reflection, since the schema's single-parent-column design makes a *reachable* cycle structurally impossible through the normal entry point — see the comment in `CategoryService`) |
| `ProductServiceTest` | Batch image-fetching matched by product id (not one query per product), and that the Specification passed to the repository is never null |
| `ProductResponseMapperTest` | Metric→imperial conversion end-to-end through the mapper, and that missing dimensions map to `null` rather than throwing |
| `UnitConverterTest` | kg→lb and cm→in conversion correctness, rounding, null-input handling, and non-terminating-division safety |
| `RegisterRequestDTOValidationTest` / `LoginRequestDTOValidationTest` | Bean Validation annotations reject blank/malformed/too-short input, accept valid input |
| `GlobalExceptionHandlerTest` | Every exception type maps to its documented HTTP status; validation errors surface the first field error's message; unexpected exceptions return a generic message and never leak the real exception's content to the client |
| `AuthIntegrationTest` (integration) | Real HTTP through the real Spring Security filter chain against a real DB: register/login/duplicate-email, refresh rotation with the old cookie verified dead afterward, **stolen-token reuse revoking the whole family including the legitimate newest token**, logout killing both the access token (verified via a real subsequent request) and the refresh token |
| `CatalogIntegrationTest` (integration) | `/search` and `/categories` against the real seeded catalog data (`CatalogDataSeeder` runs automatically against the fresh container) — pagination, category filter, price-range filter, nested category tree |
| `SecurityIntegrationTest` (integration) | SQL injection payloads in login/search don't bypass auth or crash; malformed JSON returns a clean 4xx, not a stack trace; XSS-style payloads are rejected or treated as inert data, never executed |

## A real bug these integration tests caught

`AuthIntegrationTest`'s stolen-token-reuse test initially failed against the real database even
though the equivalent Mockito unit test passed. Root cause: `AuthService.refresh()` is
`@Transactional`. On detecting reuse, it called `revokeAllByFamilyId(...)` (the "kill the whole
family" side effect) and then threw `InvalidRefreshTokenException` — an unchecked exception,
which triggers Spring's default rollback-on-`RuntimeException` behavior for the *entire*
transaction, silently undoing the revocation the instant it was needed most. A Mockito-mocked
repository can't simulate real transaction rollback, so the unit test couldn't see this at all.
Fixed by running the revocation in its own `REQUIRES_NEW` transaction (`TransactionTemplate`)
that commits independently before the exception propagates.

## Known gaps (not covered yet)

- **`ProductSpecifications`** (the Criteria API filter-building logic) isn't tested in
  isolation — it's exercised indirectly through `CatalogIntegrationTest`'s `/search` calls, but
  there's no dedicated test proving each filter method's predicate logic independently.
- No test yet for concurrent/racing requests (e.g. two simultaneous `/refresh` calls with the
  same token).
