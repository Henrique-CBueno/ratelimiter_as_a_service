## 1. Setup

- [x] 1.1 Add `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, and
      `resilience4j-micrometer` to `rls-bootstrap`'s `pom.xml` — versions resolved via the existing
      `spring-boot-dependencies` and `resilience4j-bom` imports, no explicit versions needed
- [x] 1.2 Configure `application.yml`: `management.endpoints.web.exposure.include: health,prometheus`
      and `management.endpoint.health.status.order` including `DEGRADED` between `UP` and `DOWN`

## 2. Circuit breaker registry and metrics

- [x] 2.1 Refactor `ResilienceConfig` to construct the circuit breaker via a `CircuitBreakerRegistry`
      bean (`CircuitBreakerRegistry.of(config)` → `registry.circuitBreaker("redis-rate-limit")`)
      instead of a bare `CircuitBreaker.of(...)` — same name, same config, same single global
      instance, purely so metrics have a registry to bind to
- [x] 2.2 Bind `TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)` to the
      `MeterRegistry` bean
- [x] 2.3 Confirm existing resilience adapter tests (`ResilientRateLimitEvaluationAdapterTest`,
      `CircuitOpenFallbackE2EIT`) still pass unchanged after the registry refactor

## 3. Circuit breaker health indicator

- [x] 3.1 Write a failing test for a `ReactiveHealthIndicator` that reports `UP` when the circuit
      breaker is closed or half-open, then implement it to pass
- [x] 3.2 Write a failing test for the same indicator reporting a custom `DEGRADED` status when the
      circuit breaker is open, then implement it to pass
- [x] 3.3 Wire the indicator as a bean named so it appears under `/actuator/health`'s components as
      the circuit breaker's own entry (not merged into a generic indicator) — bean name
      `circuitBreakerHealthIndicator` maps to component name `circuitBreaker` in the aggregated
      health JSON per Spring Boot's indicator-name convention

## 4. Actuator endpoint verification

- [x] 4.1 Write an e2e test (extending `AbstractE2ETest`) asserting `GET /actuator/health` returns
      `200` with overall status `UP` when the circuit breaker is closed
- [x] 4.2 Write an e2e test forcing the circuit breaker open (`transitionToOpenState()`, same
      technique as `CircuitOpenFallbackE2EIT`) and asserting the health response reflects `DEGRADED`
      for the circuit breaker component and a non-`UP` overall status, then transitioning back to
      closed — required adding `management.endpoint.health.show-details: always` to
      `application.yml`, since Spring Boot hides the per-component breakdown by default
- [x] 4.3 Write an e2e test asserting `GET /actuator/prometheus` returns `200` with circuit breaker
      metrics present in the body after at least one rate-limit check
- [x] 4.4 Write an e2e test asserting an unlisted Actuator endpoint (e.g. `/actuator/env`) returns
      `404`

**Pitfall hit:** `/actuator/prometheus` 404'd under `@SpringBootTest` even though it worked when
manually running the app. Spring Boot's test support disables metrics export by default
(`management.defaults.metrics.export.enabled=false`, applied via a `ContextCustomizerFactory`, not
a regular auto-configuration) to spare every `@SpringBootTest` the cost of real metric registration.
The old `@AutoConfigureMetrics` annotation for re-enabling this was removed in Spring Boot 3.x;
its replacement is `@AutoConfigureObservability` (`org.springframework.boot.test.autoconfigure.actuate.observability`),
added to `ActuatorE2EIT`.

## 5. Dockerfile

- [x] 5.1 Write a multi-stage `Dockerfile` at the repository root: a Maven build stage compiling the
      whole reactor and a slim JRE runtime stage running only `rls-bootstrap`'s jar — also added a
      `.dockerignore` (`**/target`, `.git`, `.idea`, `openspec`, `docs`, `*.md`) to keep the build
      context small
- [x] 5.2 Verify `docker build .` succeeds from a clean checkout and produces a runnable image
      (`docker run` the image standalone, confirm it starts and fails fast without Redis/Postgres —
      expected, since none are configured yet at this step)

**Pitfall hit (two-part):** the first build produced an ~11KB jar — the plain (non-executable) jar,
not a Spring Boot fat jar; running it failed with `no main manifest attribute`. `rls-bootstrap`'s
`spring-boot-maven-plugin` declaration had no `<executions>` binding `repackage` to any phase. That
binding normally comes for free from `spring-boot-starter-parent`'s `pluginManagement`, but this
project uses its own custom parent POM instead, so it was never configured — and nothing surfaced
the gap until now, since every prior spec's tests ran the app in-process via `@SpringBootTest`
rather than needing the packaged jar.

Binding `repackage` into `rls-bootstrap`'s own lifecycle (tried both the `package` and `verify`
phases) turned out to break `mvn verify` itself: whenever more than one `@SpringBootTest` class in
the module's Failsafe suite ran together, every one of them started failing with "Unable to find a
`@SpringBootConfiguration`" — isolated single-class runs passed, masking the problem at first. The
exact mechanism wasn't worth chasing further, since `mvn verify` staying correct matters far more
than where repackaging happens. Fixed by keeping `rls-bootstrap`'s own plugin declaration
execution-free (confirmed: full multi-class suite passes again) and instead having the
`Dockerfile` invoke repackaging as two fully separate Maven invocations, decoupled from any phase
`mvn verify` runs: `mvn -pl rls-bootstrap -am install -DskipTests` (installs every reactor module
into the image's local repo, so the module resolves as a normal dependency) followed by
`mvn -pl rls-bootstrap package org.springframework.boot:spring-boot-maven-plugin:3.5.16:repackage
-DskipTests` (the explicit-version, fully-qualified goal form, since a bare `spring-boot:repackage`
prefix and an unversioned coordinate both failed to resolve correctly outside the reactor's own
managed-plugin context). The resulting jar is ~51MB and starts correctly (fails fast on the
expected Postgres-connection-refused error with no database configured).

## 6. Docker Compose

- [x] 6.1 Write `docker-compose.yml` at the repository root: `redis`, `postgres` (with
      healthchecks), and two application services (`app1`, `app2`) built from the Dockerfile,
      sharing the same Redis/PostgreSQL, exposed on host ports 8081 and 8082 — both build directly
      from `.` (Docker's layer cache makes the second build effectively instant), rather than one
      building and the other referencing Compose's auto-derived image tag
- [x] 6.2 Verify `docker compose up --build` starts all services successfully and both app
      instances' `/actuator/health` report `UP` once Redis/PostgreSQL are ready — confirmed via
      `curl localhost:8081/actuator/health` and `localhost:8082/actuator/health`, both `UP` with
      the circuit breaker component visible

## 7. Horizontal scalability verification

- [x] 7.1 With `docker compose up` running, register a tenant and configure a resource with a small
      limit (e.g. `limit=10`) via one instance's REST API
- [x] 7.2 Send check requests for that resource and client IP alternately to `localhost:8081` and
      `localhost:8082`, past the configured limit, and record the outcome (allowed/denied count per
      instance and combined) directly in this tasks file as the verification record
- [x] 7.3 Confirm the combined allowed count across both instances equals exactly the configured
      limit — proving the two stateless instances coordinate through shared Redis state, not
      independent in-memory counters
- [x] 7.4 `docker compose down` and note completion

**Verification record (2026-08-12):** Registered a tenant via `app1` (`localhost:8081`), created
resource `/scale-check` with `FIXED_WINDOW`, `limit=10`, `windowSeconds=60`. Sent 15
`POST /api/v1/ratelimit/check` requests for the same resource and client IP (`198.51.100.77`),
alternating strictly between `localhost:8081` and `localhost:8082` (odd requests → app1, even → app2):

| Instance | Allowed | Denied |
|---|---|---|
| app1 (:8081) | 5 | 3 |
| app2 (:8082) | 5 | 2 |
| **Combined** | **10** | **5** |

Combined allowed = 10, exactly the configured limit, and every request past the 10th was denied
(`429`) on *both* instances. Had the two processes been counting independently (no shared state),
each would have allowed its own 10 (20 combined) before either started denying — the result
directly confirms the stateless/horizontal-scalability design holds under a real multi-process
deployment, not just within a single test JVM. Stack torn down with `docker compose down`
afterward.

## 8. Verification and wrap-up

- [x] 8.1 Run `mvn verify` across every module; confirm every scenario in
      `specs/observability/spec.md` is covered by a passing test (the `containerized-deployment`
      capability is verified manually per Group 5-7, not by the Maven test suite, per the design's
      decision 7) — full reactor `mvn verify` is green; `observability` scenarios covered by
      `CircuitBreakerHealthIndicatorTest` and `ActuatorE2EIT`
- [x] 8.2 Update `README.md`: Actuator endpoints, and Docker Compose usage (`docker compose up
      --build`, the two exposed ports, how to reach each instance)
- [x] 8.3 Commit work on `feature/observability-docker` following git-flow commit conventions (no AI
      co-authorship line)
