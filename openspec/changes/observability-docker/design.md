## Context

`rls-bootstrap` currently has no Actuator, no metrics, and no way to run outside `mvn
spring-boot:run` or a bare `java -jar`. Every previous spec was verified through `mvn verify`
(unit tests, Testcontainers-based integration tests within a single JVM) — none of that proves the
"stateless, horizontally scalable" claim from spec 1 under an actual multi-process deployment. This
is the last spec in the architecture plan, and its explicit job is closing that gap: give the app
basic operational visibility, and prove — by actually running two instances against shared
Redis/PostgreSQL — that the design holds up outside a single test JVM.

## Goals / Non-Goals

**Goals:**
- `/actuator/health` reports the app's own dependencies, including the circuit breaker's state,
  which nothing currently surfaces.
- `/actuator/prometheus` exposes Micrometer metrics, including the circuit breaker's existing
  Resilience4j metrics (currently collected but bound to nothing).
- A single `docker compose up --build` starts Redis, PostgreSQL, and two app instances, letting
  anyone reproduce the horizontal-scalability proof without hand-wiring containers.
- Demonstrate (not just assert) that two stateless app instances sharing one Redis coordinate
  rate-limit counters correctly.

**Non-Goals:**
- No authentication/authorization on Actuator endpoints — acceptable for this project's current
  scope (a demo/reference service, not a hardened production deployment); flagged as a risk below,
  not solved here.
- No Kubernetes manifests, no reverse proxy / load balancer in front of the two app instances — the
  architecture plan explicitly deferred Kubernetes, and a load balancer isn't needed to prove the
  coordination claim (the verification script talks to each instance's port directly).
- No distributed tracing, no log aggregation — out of scope for "observability" as this spec defines
  it (health + metrics only).
- No permanent automated test for the 2-instance proof (see decision below) — it's a manual,
  documented verification step run once during implementation.

## Decisions

### 1. Keep Spring Boot's auto-configured Redis/R2DBC health indicators; add only a circuit breaker one
`spring-boot-starter-actuator` auto-configures health indicators for both Redis (reactive) and R2DBC
once it's on the classpath alongside the existing `ReactiveRedisConnectionFactory` and
`ConnectionFactory` beans — they already do the right thing (a real connectivity check). Writing
custom replacements would duplicate that with no behavioral gain. The one real gap is the circuit
breaker: the project wires Resilience4j directly (`ResilienceConfig`, from the `rest-api` change)
rather than via the `resilience4j-spring-boot3` starter, so no health indicator for it exists yet.
Add exactly one custom `ReactiveHealthIndicator` for it.

### 2. Circuit breaker moves from a bare `CircuitBreaker` to a `CircuitBreakerRegistry`
`ResilienceConfig` currently builds the circuit breaker via `CircuitBreaker.of("redis-rate-limit",
config)` — a standalone instance with no registry. Binding Resilience4j's metrics to Micrometer
(`TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(...)`) needs a `CircuitBreakerRegistry`, so
`ResilienceConfig` changes to `CircuitBreakerRegistry.of(config)` then
`registry.circuitBreaker("redis-rate-limit")`. Behaviorally identical (same name, same config,
still the single global instance from design decision 3 of the `rest-api` change) — this is purely
so the existing metrics have somewhere to bind to.

### 3. Circuit breaker health maps `OPEN` to a custom `DEGRADED` status, not `DOWN`
When the circuit is open, the app is still serving traffic via the configured fallback policy
(`resilient-evaluation-fallback` capability) — it isn't down, it's degraded, which is the exact
vocabulary the domain already uses (`RateLimitDecision.degraded()`). Reporting `OPEN` as `DOWN`
would be misleading (nothing stopped working) and could trigger orchestrator restarts that don't
fix anything (the dependency, not the app, is unhealthy). `CLOSED`/`HALF_OPEN` map to `UP`, `OPEN`
maps to a registered custom `Status("DEGRADED")`, ranked between `UP` and `DOWN` via
`management.endpoint.health.status.order`.

### 4. Actuator exposure is deliberately narrow: `health` and `prometheus` only
`management.endpoints.web.exposure.include: health,prometheus` (not `*`) — endpoints like `env` or
`beans` reveal configuration/internals with no operational benefit for this project and are exactly
the kind of surface the Non-Goals section declines to secure. Keeping the exposed set minimal is a
cheaper mitigation than adding auth infrastructure this spec isn't scoped to build.

### 5. Docker build runs `mvn package -DskipTests` inside the build stage
The multi-stage `Dockerfile`'s build stage compiles the whole reactor (it needs every module
`rls-bootstrap` depends on) but skips tests: the integration test suite needs Testcontainers, which
needs a Docker daemon — unavailable *inside* a Docker build without Docker-in-Docker, which this
project has no other reason to set up. Tests already run via `mvn verify` in normal local/CI
workflows before an image would ever be built; skipping them again inside the build stage avoids
solving an already-solved problem twice.

### 6. Two app services in Compose, not `docker compose up --scale`
`docker-compose.yml` defines `app1` and `app2` explicitly (same image, host ports 8081/8082) rather
than using `--scale app=2` behind a single service. Explicit ports let the verification step address
each instance individually without needing a load balancer or Compose's internal round-robin DNS —
directly matching what the verification needs (send request N to instance A, request N+1 to
instance B, confirm the shared counter is still exact).

### 7. The 2-instance proof is a manual, documented verification step, not a permanent test
Proving coordination across two real OS processes doesn't fit the Maven/Testcontainers-based
automated suite the rest of the project uses (that suite runs everything, including "concurrency"
tests, within one JVM against ephemeral containers). Standing up a permanent CI job that drives
`docker compose` would be disproportionate to what one architecture-plan checkbox needs. This
mirrors spec 2's within-JVM concurrency test in spirit (N concurrent calls, assert exactly `limit`
succeed) but run across two real app processes; the steps and observed result are written into
`tasks.md` as the record of having done it.

## Risks / Trade-offs

- **[Risk]** Actuator endpoints are unauthenticated. → **Mitigation**: exposure is limited to
  `health` and `prometheus` (decision 4); full Actuator security (Spring Security rules on
  `/actuator/**`, or network-level restriction) is explicitly out of scope and would be a future
  spec if this project moved toward production hardening.
- **[Risk]** A custom, unregistered `DEGRADED` health status could be mis-ranked by Spring Boot's
  default status aggregator (which only knows `UP`/`DOWN`/`OUT_OF_SERVICE`/`UNKNOWN` by default).
  → **Mitigation**: explicitly configure `management.endpoint.health.status.order` to include
  `DEGRADED`, and add a test asserting the aggregated status is `DEGRADED` (not silently `UNKNOWN`
  or miscategorized) when the circuit is open.
- **[Risk]** Flyway migrations could race if both app instances start simultaneously and both
  attempt `migrate()` against the same empty schema. → **Mitigation**: none needed — Flyway uses an
  advisory lock on `flyway_schema_history` specifically to make concurrent migration attempts from
  multiple instances safe (the second instance waits, sees the schema already at the target
  version, and proceeds); this is documented Flyway behavior, not something this project has to
  build.

## Migration Plan

Purely additive: new Actuator dependency/config, one new health indicator, a registry-based
refactor of the circuit breaker bean (no behavior change), and new deployment artifacts (Dockerfile,
docker-compose.yml). No existing endpoints, schemas, or Redis key formats change. Nothing to roll
back beyond reverting the merge.
