## Context

Specs 1–3 delivered a complete but unreachable backend: domain aggregates and strategies (spec 1),
a Redis adapter proven atomic under concurrency (spec 2), and a PostgreSQL adapter for tenant and
resource persistence (spec 3). Nothing composes them, nothing runs, and nothing is reachable over a
network. This spec adds the orchestration layer (`rls-application` use cases), the resilience
decorator around the Redis adapter, the REST API, and the first real Spring Boot application
(`rls-bootstrap`) — the point at which "send a resource and an IP, get back authorized or not"
becomes something a real HTTP client can do.

## Goals / Non-Goals

**Goals:**
- Application use cases that orchestrate specs 1–3's ports without any of them knowing about HTTP.
- A REST API exposing tenant onboarding, resource management, and the check endpoint, authenticated
  by API token, with RFC 7807 error responses.
- A circuit breaker around the Redis evaluation call specifically, with per-resource fallback
  policy honored when the circuit is open — the reliability requirement stated since spec 1's
  design ("circuit breaker... fallback policy configurável por tenant").
- A runnable `rls-bootstrap` module wiring every port to its spec 2/3/4 adapter, provable via
  end-to-end tests against real Redis and PostgreSQL (Testcontainers).

**Non-Goals:**
- No Thymeleaf front end (spec 5) — the REST API is this spec's only inbound surface besides tests.
- No observability stack (Micrometer/Prometheus/health indicators beyond the bare minimum needed to
  start the app) or Docker Compose for manual local running — both spec 6.
- No caching of tenant/resource lookups on the check hot path — the Postgres-per-request lookup
  noted as a future optimization since spec 1's design remains un-addressed here; this spec makes
  the system correct and observable-via-tests first.
- No rate limiting or abuse protection on the onboarding endpoint itself (e.g., CAPTCHA, email
  verification) — out of scope for an MVP backend spec; a product concern for later if needed.

## Decisions

**1. Application use cases are one class per use case, each with a single public reactive method.**
Consistent with the ports-and-use-cases vocabulary already used in `docs/architecture-plan.md`
since spec 1 (`RegisterTenantUseCase`, `CheckRateLimitUseCase`, etc.). Each use case depends only on
ports (interfaces), never on adapters directly, and returns `Mono<T>`/`Mono<Void>`. Alternative
considered: a single "TenantService"/"ResourceService" façade per aggregate. Rejected — per-use-case
classes keep each orchestration flow's dependencies and error handling legible in isolation and
match the granular use-case list already documented in the architecture plan.

**2. The circuit breaker wraps only `RateLimitEvaluationPort.evaluate(...)`, not persistence calls.**
Persistence (Postgres) failures during onboarding/resource management should surface as ordinary
5xx errors — there is no defined "fallback" concept for "create a tenant when the database is
down." The circuit breaker's entire reason to exist, per spec 1's design, is the check endpoint's
hot-path dependency on Redis, where a defined fallback (allow/deny per policy) exists. Scoping it
narrowly avoids inventing undefined fallback semantics for operations that have none.

**3. One global `CircuitBreaker` instance (named `"redis-rate-limit"`), not one per tenant/resource.**
Restates and implements spec 1's design decision: the circuit reflects the health of the shared
Redis dependency, not any single tenant's traffic pattern — one circuit per tenant would dilute
failure detection across thousands of low-traffic instances and misattribute shared infrastructure
failure to individual tenants. `rls-adapter-resilience`'s `ResilientRateLimitEvaluationAdapter`
decorates the spec 2 Redis adapter with a single shared `CircuitBreaker`, applied via
Resilience4j's reactor integration (`CircuitBreakerOperator`, `resilience4j-reactor`).

**4. Fallback policy resolution happens in `CheckRateLimitUseCase`, not in the resilience adapter.**
`ResilientRateLimitEvaluationAdapter` only knows "the circuit is open" or "the call failed" — it has
no access to the `Tenant`/`RateLimitResource` needed to resolve the effective fallback policy
(`RateLimitResource.resolveFallbackPolicy(tenant)`, spec 1). So the adapter surfaces a distinct
`RateLimitEvaluationUnavailableException` on circuit-open/failure, and `CheckRateLimitUseCase`
catches it, resolves the policy, and builds a degraded `RateLimitDecision` via the domain's existing
`RateLimitDecision.asDegraded()` (spec 1) — keeping the resilience adapter generic and the
policy-aware decision squarely in the use case that already has both aggregates loaded.

**5. Manual Resilience4j bean wiring in `rls-bootstrap`, not the `resilience4j-spring-boot3`
autoconfiguration starter.**
Every other adapter in this project (Redis, R2DBC) is wired by hand rather than through Spring Boot
autoconfiguration magic, keeping wiring explicit and traceable in one place. Adding the
autoconfigured starter here would introduce a different, less consistent configuration style
(YAML-property-driven circuit breaker config) for just this one dependency. `rls-bootstrap` builds
the single `CircuitBreaker` via `CircuitBreakerConfig.custom()...build()` in a plain
`@Configuration` class, matching the project's existing wiring style.

**6. Cross-tenant resource access returns `404`, not `403`.**
`GET/PUT/DELETE /api/v1/resources/{id}` for a resource id that exists but belongs to a different
tenant is treated identically to a nonexistent id — both `404`. Alternative considered: `403
Forbidden` for "exists but not yours." Rejected — returning `403` confirms the resource id exists at
all, letting a caller enumerate other tenants' resource ids by probing status codes; `404` leaks
nothing.

**7. Token format: `rls_live_<32 random URL-safe characters>`, first 12 characters stored as the
display prefix, full token *fingerprinted* (not `SecretHasherPort`-hashed) before storage.**
Only the fingerprint is ever persisted (spec 3); the raw token is returned to the caller exactly
once, at issuance/rotation time, and never again. The `rls_live_` prefix follows the common
"identifiable token prefix" convention (recognizable in logs/secret scanners) without needing a
database lookup to tell tokens apart from other secrets.
**Correction discovered during this spec's implementation** (supersedes spec 3's proposal wording
that one `SecretHasherPort` would serve both passwords and tokens): `SecretHasherPort` is
BCrypt-backed and salted, so `hash(x)` returns a *different* value on every call for the same
input — correct for password verification (always "look up the tenant by email, then check
BCrypt.matches against that tenant's stored hash"), but incompatible with
`TenantRepositoryPort.findByActiveTokenHash`, which must locate the tenant *by* the stored value
without knowing who it belongs to first. A random 24-byte token already has enough entropy that it
does not need a salted, deliberately-slow hash to resist brute force — a password does, because
humans choose low-entropy passwords. `ApiTokenGenerator` (new, `rls-application`) therefore adds a
`fingerprint(rawToken)` method — a plain deterministic SHA-256 digest, needing no port/adapter
since it has no swappable implementation concern — used everywhere a token's stored `tokenHash` is
computed or looked up. `SecretHasherPort`/BCrypt remains exclusively for tenant passwords. See the
`## MODIFIED Requirements` in `specs/tenant-persistence/spec.md` (this change) for the corrected
capability requirement.

**8. Errors map to RFC 7807 `ProblemDetail` via WebFlux's built-in `ProblemDetail`/
`ResponseStatusException` support**, not a hand-rolled error envelope. Domain/application exceptions
(`DuplicateEmailException`, `DuplicateResourceKeyException`, "resource not found", "unauthorized")
are translated to the appropriate HTTP status by a small `@ControllerAdvice`
(`RestExceptionHandler`) in `rls-adapter-rest`, keeping controllers free of try/catch boilerplate.

**9. Authentication is a `WebFilter`, not Spring Security.**
No authorization framework exists yet, and the auth requirement here is narrow: extract
`Authorization: Bearer <token>`, hash it, resolve the owning tenant via
`TenantRepositoryPort.findByActiveTokenHash` (spec 3), and place the resolved `Tenant` on the
exchange for downstream handlers — or short-circuit with `401`. A single `WebFilter`
(`ApiTokenAuthenticationWebFilter`) implements this directly; introducing full Spring Security for
one bearer-token check would add a large, mostly-unused dependency and its own configuration
surface. Revisit if/when spec 5's dashboard needs session-based login (a different, cookie-based
concern that can be layered on independently).

**10. `rls-bootstrap` is the only module allowed to depend on every other module.**
Consistent with the hexagonal layering established since spec 1: `rls-bootstrap` is the composition
root, wiring `rls-application`'s use cases to the concrete adapters from `rls-adapter-redis`,
`rls-adapter-persistence`, `rls-adapter-resilience`, and `rls-adapter-rest`. No adapter module
depends on another adapter module.

## Risks / Trade-offs

- [Risk] The check endpoint now touches Postgres (resource lookup) and Redis (evaluation) on every
  request — the highest-latency path in the system → Mitigation: explicitly accepted as a Non-Goal
  or optimization deferred past this spec (per spec 1's design note); nothing here regresses
  correctness, and the e2e tests validate behavior, not latency budgets.
- [Risk] A single global circuit breaker means a legitimately failing individual Redis shard (in a
  future sharded deployment) could trip the circuit for all tenants → Mitigation: out of scope for
  the current single-Redis-instance architecture; revisit if/when Redis sharding is introduced.
- [Risk] The hand-rolled `WebFilter` auth is easy to get subtly wrong (timing side channels on hash
  comparison, missing the unauthenticated allowlist for onboarding) → Mitigation: `SecretHasherPort`
  (BCrypt) already performs constant-time comparison internally; the filter's allowlist is a single
  explicit path check covered by a dedicated test asserting registration works without a token and
  every other endpoint rejects requests without one.
- [Trade-off] Returning `404` for cross-tenant resource access (Decision 6) means legitimate callers
  get the same error for "typo'd an id" and "tried someone else's id" — acceptable; the API doesn't
  need to distinguish these for a caller with no legitimate reason to know the difference.

## Migration Plan

Not applicable — no existing deployed version of this API exists yet. `rls-bootstrap` is new.

## Open Questions

- None blocking. Whether the check endpoint should also validate that the calling token's tenant
  matches a `tenantId` implied elsewhere is moot — the token *is* how the tenant is identified,
  there is no separate tenant identifier in the check request.
