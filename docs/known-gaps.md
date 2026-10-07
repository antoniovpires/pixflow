# Known gaps and technical debt

Honest list of what PixFlow does not do yet, and what to tackle next.
Last updated: 2026-10-06. Priority: **P1** = fix before calling it production-like, **P2** = should do, **P3** = polish.

## Authentication and security

| # | Gap | Why it matters | How to tackle | Priority |
|---|-----|----------------|---------------|----------|
| S1 | **No refresh tokens.** Access tokens live 15 min, then the user must log in again. | Poor UX; the usual fix is short access token + revocable refresh token. | `refresh_tokens` table (hashed token, user, expiry, revoked), `POST /auth/refresh` with rotation, `POST /auth/logout` revokes it. | P1 |
| S2 | **No rate limiting on `/auth/login`.** | Passwords can be brute-forced. | Bucket4j or a small in-memory/Redis counter per IP + email; return 429. Redis is a natural first use. | P1 |
| S3 | **Access tokens cannot be revoked.** A stolen token works until `exp` (max 15 min). | Accepted trade-off of stateless JWTs. | Keep `exp` short; optionally a deny-list keyed by `jti` in Redis. Document in an ADR. | P2 |
| S4 | **HS256 with one shared secret.** | Fine for one app; any service that can verify can also forge. | Move to RS256/ES256 (private key signs, public key verifies) if a second service ever validates tokens. | P3 |
| S5 | **Secret management.** `JWT_SECRET` is a plain env var. | No rotation story. | Document rotation (accept old + new key during a window); use a secret manager when deployed. | P3 |
| S6 | **No roles / admin concept.** | `AccountService.getAll()` and `update()` exist but have no HTTP exposure because anything admin-like was removed. | Add roles claim + `@PreAuthorize` only if an admin use case appears. | P3 |
| S7 | **Actuator beyond `/health` is locked, but not reviewed.** | Metrics/info endpoints could leak details when exposed. | Review `management.endpoints.*` exposure before any deploy. | P2 |
| S8 | **Registration is open and unverified.** No email confirmation, no password policy beyond length 8-72. | Fake accounts; weak passwords. | Email verification flow; check against common-password list. | P3 |

## Transfers and money

| # | Gap | Why it matters | How to tackle | Priority |
|---|-----|----------------|---------------|----------|
| T1 | **Failed transfers are not recorded.** Any failure rolls back everything, so no `FAILED` row exists. | No audit trail of rejected payments. | Record the attempt in a separate transaction (`REQUIRES_NEW`) from the retry/orchestration layer, after the main transaction fails. | P2 |
| T2 | **No state-transition guards on `Transfer`.** `markCompleted()` works on a `REVERSED` transfer. | Invalid lifecycle states become possible. | Allow only legal transitions (e.g. `PENDING` -> `SUCCESS`/`FAILED`/`CANCELLED`; `SUCCESS` -> `REVERSED`), throw otherwise. Test each. | P2 |
| T3 | **No reversal flow.** The `REVERSED` status exists but nothing creates reversing ledger entries. | Refunds are a core payments feature. | New service method that writes opposite ledger entries and flips the status, idempotently. | P2 |
| T4 | **Retry has no backoff.** `TransferRetryService` retries immediately, up to `MAX_ATTEMPTS` = 5. | Under heavy contention it hammers the DB. | Tiny randomized sleep (jitter) between attempts. Decide and record in ADR-0002. | P3 |
| T5 | **Idempotency key is a body field.** | Convention is an `Idempotency-Key` HTTP header; a replay should also return 200, not 201. | Read the header in the controller; return 200 on replay. | P3 |
| T6 | **Idempotency records never expire.** | Table grows forever; keys are only meaningful for a window (e.g. 24 h). | Scheduled cleanup or a TTL column. | P3 |
| T7 | **No ledger invariant check.** | Nothing asserts that total debits equal total credits. | Add a verification query/test (and optionally a scheduled reconciliation job). | P2 |

## Database and entities

| # | Gap | How to tackle | Priority |
|---|-----|---------------|----------|
| D1 | **Migration V5 was edited after being committed** (`DEFAULT 'default_value' UNIQUE`). Flyway checksums break any DB that applied the original; the default also lets inserts silently omit the key. | Restore the committed V5; if existing rows must be handled, do it in a new migration. Rule: never edit an applied migration. | P1 |
| D2 | **No indexes on foreign keys** (`transfers.pix_key_id`, `source/target_account_id`, `ledger_entries.transfer_id`, `account_id`, `pixkeys.account_id`). Postgres does not create them. | New migration adding them, matched to the actual `findBy...` queries. | P2 |
| D3 | **`ledger_entries.updated_at` exists** although ledger rows must be immutable. | Drop the column and `@UpdateTimestamp`; consider a DB trigger or no-update grant. | P2 |
| D4 | **`@ManyToOne` on `Transfer`/`LedgerEntry` are eager and without `nullable = false`.** | `fetch = LAZY`, `optional = false`; watch for N+1 in list endpoints (use fetch joins/DTO projections). | P2 |
| D5 | **Manual `updatedAt = now()` in `Transfer.markX()`** duplicates `@UpdateTimestamp`. | Delete the manual assignments. | P3 |
| D6 | **`Status` enum name is too generic.** | Rename to `TransferStatus`. | P3 |
| D7 | **`DECIMAL(10,2)` caps balances at 99,999,999.99.** | Fine for a demo; widen before real use. | P3 |
| D8 | **Dead code:** `AccountService.getAll()/update()` and `PixKeyService.getAll()` are no longer reachable over HTTP. | Delete them and their tests, or give them a real purpose. | P3 |
| D9 | **PixKey normalization** (email lowercased, phone/CPF digits) - verify it is covered by tests (country code handling, trimming, `Locale.ROOT`, null guard). | Add tests; fix what they reveal. | P2 |

## API and operations

| # | Gap | How to tackle | Priority |
|---|-----|---------------|----------|
| A1 | **No pagination** on `GET /transfers`, `/accounts/{id}/ledger`, `/pixkeys`. | `Pageable` + page response DTO. | P2 |
| A2 | **No API documentation.** | springdoc-openapi (check Spring Boot 4 compatibility), Swagger UI. | P2 |
| A3 | **No CORS configuration**, needed once the Angular frontend calls the API. | `CorsConfigurationSource` bean with an explicit origin list. | P2 |
| A4 | **No structured logging / correlation ids / metrics.** | MDC request id filter, Micrometer + Actuator. | P3 |
| A5 | **No CI pipeline.** | GitHub Actions: `./mvnw clean verify` (Testcontainers works on `ubuntu-latest`). | P1 for a portfolio |
| A6 | **No README** (setup, `JWT_SECRET`, curl walkthrough, architecture, this list). | Write it; link the ADRs. | P1 for a portfolio |
| A7 | **No Dockerfile / full compose** (app + Postgres). | Multi-stage Dockerfile; add the app to `infra/docker-compose.yml`. | P2 |
| A8 | **Kafka, Redis, Angular frontend not started.** | Only add where there is a concrete reason (events for completed transfers, Redis for rate limiting/idempotency). Record the decision in an ADR either way. | P3 |

## Frontend (added 2026-10-06)

| # | Gap | How to tackle | Priority |
|---|-----|---------------|----------|
| F1 | Send money, PIX keys and Statement screens are placeholders ("Soon"). | Build next, one vertical slice each. Generate the idempotency key once per form, not per click. | P1 |
| F2 | No "who owns this key?" lookup, so the send-confirmation screen cannot show a recipient name. | Backend endpoint that resolves a key to a masked display name. | P2 |
| F3 | Session is lost after 15 min (no refresh token) and the token sits in `sessionStorage` (readable by XSS). | Refresh-token flow with an HttpOnly cookie (see S1). Never render untrusted HTML meanwhile. | P1 |
| F4 | No demo/seed data; an empty dashboard makes poor screenshots. | Seed script or dev-profile `CommandLineRunner` creating two users, keys and some transfers. | P1 |
| F5 | Dark theme only; no light mode. | Theme tokens already centralised in `styles.css` (`@theme`). | P3 |
| F6 | No end-to-end browser tests in the repo (verified manually with Playwright). | Add Playwright to CI against a Testcontainers-backed backend. | P2 |
| F7 | `npm@10.9.3` pinned by Angular hits an npm resolver bug (`edgesOut`). | Use `npx -y npm@11 install ...` or upgrade npm. | P3 |

## Development notes (so they are not rediscovered)
- The IDE writes class files into `target/`, so `./mvnw compile` can falsely pass. Use `./mvnw clean test` to verify.
- Tests that need real concurrency (`TransferConcurrencyTests`, `AuthFlowIntegrationTests`) use `@SpringBootTest` + Testcontainers, not `@DataJpaTest`.
- The test JWT secret lives in `src/test/resources/config/application.properties`; production reads `JWT_SECRET`.
