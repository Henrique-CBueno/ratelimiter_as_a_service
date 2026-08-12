## Why

Specs 1–3 built a complete, independently-tested backend: the domain core, a Redis adapter proven
atomic under concurrency, and a PostgreSQL adapter for tenant/resource persistence. None of it is
reachable yet — there is no HTTP surface, no running application, and nothing composing the pieces
together. This change closes that gap: it adds the application-layer use cases that orchestrate the
domain and the three adapters built so far, exposes them over a reactive REST API (onboarding,
resource management, and the rate-limit check endpoint itself), adds the circuit breaker that
protects the check endpoint's hot path when Redis is unavailable, and produces the first real,
runnable Spring Boot application (`rls-bootstrap`). After this spec, the product described in the
original request — "send a resource and an IP, get back authorized or not" — actually exists and
runs end to end.

## What Changes

- Add application use cases to `rls-application`: `RegisterTenantUseCase`,
  `AuthenticateTenantUseCase`, `RotateApiTokenUseCase`, `CreateResourceUseCase`,
  `UpdateResourceUseCase`, `ListResourcesUseCase`, `DeleteResourceUseCase` (soft delete via
  `enabled=false`), and `CheckRateLimitUseCase` — each orchestrating the domain aggregates and the
  ports from specs 1–3 (`TenantRepositoryPort`, `ResourceRepositoryPort`, `SecretHasherPort`,
  `RateLimitEvaluationPort`).
- Add the `rls-adapter-resilience` Maven module: a `ResilientRateLimitEvaluationAdapter` that
  decorates `RateLimitEvaluationPort` (the Redis adapter from spec 2) with a Resilience4j circuit
  breaker. The circuit itself is a single global instance per physical dependency ("redis"); what's
  configurable per tenant/resource is only the *fallback policy* applied when that circuit is open
  (`FAIL_OPEN` vs `FAIL_CLOSED`, from spec 1's domain model).
- Add the `rls-adapter-rest` Maven module: reactive WebFlux controllers exposing:
  - `POST /api/v1/tenants` (public onboarding) → `201 {tenantId, apiToken}`
  - `POST /api/v1/tokens/rotate` (authenticated)
  - `POST /api/v1/resources`, `GET /api/v1/resources`, `GET/PUT /api/v1/resources/{id}`,
    `DELETE /api/v1/resources/{id}` (soft delete)
  - `POST /api/v1/ratelimit/check` → `{allowed, limit, remaining, resetAt, retryAfterSeconds,
    strategy}` plus `RateLimit-*`/`Retry-After` headers, `200`/`429`
  - Authentication via `Authorization: Bearer <token>` for every endpoint except tenant
    registration, resolved against `TenantRepositoryPort.findByActiveTokenHash`.
  - Errors as RFC 7807 `ProblemDetail` (WebFlux's built-in support).
- Add `rls-bootstrap`: the first real Spring Boot application module, wiring every port to its
  concrete adapter from specs 2–4 and exposing the REST API over HTTP. This is the first module
  that actually starts a server.
- Add end-to-end tests (`@SpringBootTest` + Testcontainers for Redis and PostgreSQL) covering the
  full request path: register a tenant, create a resource, call check repeatedly until the
  configured limit is exceeded, confirm `429` and headers; and a circuit-open scenario confirming
  the configured fallback policy is honored when Redis is unreachable.

## Capabilities

### New Capabilities
- `tenant-onboarding-api`: REST endpoints for tenant registration and API token rotation.
- `resource-management-api`: REST endpoints for creating, listing, updating, and disabling a
  tenant's rate-limit resources.
- `rate-limit-check-endpoint`: the authenticated REST endpoint that answers "is this
  resource+IP request authorized," including its HTTP status/header contract.
- `resilient-evaluation-fallback`: the circuit-breaker-backed fallback behavior applied when the
  rate-limit evaluation dependency (Redis) is unavailable, per the tenant/resource's configured
  fallback policy.

### Modified Capabilities
(none — this change composes and exposes the capabilities from specs 1–3 over HTTP; it does not
change their underlying requirements)

## Impact

- **New dependency surface**: the application is, for the first time, reachable over HTTP; also the
  first spec where Redis and PostgreSQL are both required simultaneously (by `rls-bootstrap`) rather
  than independently per-adapter-module.
- **New Maven modules**: `rls-adapter-resilience`, `rls-adapter-rest`, `rls-bootstrap`.
- **`rls-application`** gains its first use cases (previously only ports existed) — this is where
  the orchestration logic the design docs referenced since spec 1 (e.g., resolving a resource's
  effective fallback policy, mapping a circuit-open error to a degraded decision) actually lives.
- No changes to `rls-domain`, `rls-adapter-redis`, or `rls-adapter-persistence` — this spec composes
  them, it does not modify their behavior.
- Out of scope for this spec: the Thymeleaf front end (spec 5, which will reuse these same use
  cases as a second inbound adapter) and observability/Docker Compose (spec 6).
