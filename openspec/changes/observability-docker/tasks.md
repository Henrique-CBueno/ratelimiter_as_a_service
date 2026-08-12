## 1. Setup

- [ ] 1.1 Add `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, and
      `resilience4j-micrometer` to `rls-bootstrap`'s `pom.xml`
- [ ] 1.2 Configure `application.yml`: `management.endpoints.web.exposure.include: health,prometheus`
      and `management.endpoint.health.status.order` including `DEGRADED` between `UP` and `DOWN`

## 2. Circuit breaker registry and metrics

- [ ] 2.1 Refactor `ResilienceConfig` to construct the circuit breaker via a `CircuitBreakerRegistry`
      bean (`CircuitBreakerRegistry.of(config)` → `registry.circuitBreaker("redis-rate-limit")`)
      instead of a bare `CircuitBreaker.of(...)` — same name, same config, same single global
      instance, purely so metrics have a registry to bind to
- [ ] 2.2 Bind `TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)` to the
      `MeterRegistry` bean
- [ ] 2.3 Confirm existing resilience adapter tests (`ResilientRateLimitEvaluationAdapterTest`,
      `CircuitOpenFallbackE2EIT`) still pass unchanged after the registry refactor

## 3. Circuit breaker health indicator

- [ ] 3.1 Write a failing test for a `ReactiveHealthIndicator` that reports `UP` when the circuit
      breaker is closed or half-open, then implement it to pass
- [ ] 3.2 Write a failing test for the same indicator reporting a custom `DEGRADED` status when the
      circuit breaker is open, then implement it to pass
- [ ] 3.3 Wire the indicator as a bean named so it appears under `/actuator/health`'s components as
      the circuit breaker's own entry (not merged into a generic indicator)

## 4. Actuator endpoint verification

- [ ] 4.1 Write an e2e test (extending `AbstractE2ETest`) asserting `GET /actuator/health` returns
      `200` with overall status `UP` when the circuit breaker is closed
- [ ] 4.2 Write an e2e test forcing the circuit breaker open (`transitionToOpenState()`, same
      technique as `CircuitOpenFallbackE2EIT`) and asserting the health response reflects `DEGRADED`
      for the circuit breaker component and a non-`UP` overall status, then transitioning back to
      closed
- [ ] 4.3 Write an e2e test asserting `GET /actuator/prometheus` returns `200` with circuit breaker
      metrics present in the body after at least one rate-limit check
- [ ] 4.4 Write an e2e test asserting an unlisted Actuator endpoint (e.g. `/actuator/env`) returns
      `404`

## 5. Dockerfile

- [ ] 5.1 Write a multi-stage `Dockerfile` at the repository root: a Maven build stage compiling the
      whole reactor (`mvn -pl rls-bootstrap -am package -DskipTests`) and a slim JRE runtime stage
      running only `rls-bootstrap`'s jar
- [ ] 5.2 Verify `docker build .` succeeds from a clean checkout and produces a runnable image
      (`docker run` the image standalone, confirm it starts and fails fast without Redis/Postgres —
      expected, since none are configured yet at this step)

## 6. Docker Compose

- [ ] 6.1 Write `docker-compose.yml` at the repository root: `redis`, `postgres` (with
      healthchecks), and two application services (`app1`, `app2`) built from the Dockerfile,
      sharing the same Redis/PostgreSQL, exposed on host ports 8081 and 8082
- [ ] 6.2 Verify `docker compose up --build` starts all services successfully and both app
      instances' `/actuator/health` report `UP` once Redis/PostgreSQL are ready

## 7. Horizontal scalability verification

- [ ] 7.1 With `docker compose up` running, register a tenant and configure a resource with a small
      limit (e.g. `limit=10`) via one instance's REST API
- [ ] 7.2 Send check requests for that resource and client IP alternately to `localhost:8081` and
      `localhost:8082`, past the configured limit, and record the outcome (allowed/denied count per
      instance and combined) directly in this tasks file as the verification record
- [ ] 7.3 Confirm the combined allowed count across both instances equals exactly the configured
      limit — proving the two stateless instances coordinate through shared Redis state, not
      independent in-memory counters
- [ ] 7.4 `docker compose down` and note completion

## 8. Verification and wrap-up

- [ ] 8.1 Run `mvn verify` across every module; confirm every scenario in
      `specs/observability/spec.md` is covered by a passing test (the `containerized-deployment`
      capability is verified manually per Group 5-7, not by the Maven test suite, per the design's
      decision 7)
- [ ] 8.2 Update `README.md`: Actuator endpoints, and Docker Compose usage (`docker compose up
      --build`, the two exposed ports, how to reach each instance)
- [ ] 8.3 Commit work on `feature/observability-docker` following git-flow commit conventions (no AI
      co-authorship line)
