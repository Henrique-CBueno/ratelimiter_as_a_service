## Why

The application has no operational visibility (no health checks, no metrics) and no packaged way
to run it — every prior spec was verified via `mvn verify` and manual `spring-boot:run`, never as a
containerized, independently deployable artifact. Spec 6 closes the architecture plan's final gap:
Actuator health/metrics endpoints an operator or load balancer can act on, and Docker packaging
that lets the stateless/horizontally-scalable design be demonstrated for real — multiple app
instances behind the same Redis/PostgreSQL, not just asserted by code review.

## What Changes

- Add Spring Boot Actuator to `rls-bootstrap`, exposing `/actuator/health` and
  `/actuator/prometheus` (Micrometer + `micrometer-registry-prometheus`).
- Keep Spring Boot's auto-configured health indicators for Redis and R2DBC as-is — they already
  check connectivity correctly; no gap to fill there.
- Add one new custom health indicator for the Resilience4j circuit breaker's state (`CLOSED` /
  `OPEN` / `HALF_OPEN`), since nothing currently reports it — the project doesn't use the
  `resilience4j-spring-boot3` starter (design decision 3 of the `rest-api` change wires
  `CircuitBreaker` explicitly), so no such indicator exists yet.
- Bind the circuit breaker's existing Resilience4j metrics (call counts, failure rate, state
  transitions) to Micrometer so they're visible via `/actuator/prometheus`.
- Add a multi-stage `Dockerfile` (build stage compiles the Maven reactor, runtime stage is a slim
  JRE image running only `rls-bootstrap`'s jar).
- Add a `docker-compose.yml` at the repo root: Redis, PostgreSQL, and **two** app instances (same
  image, different host ports) sharing the same backing services — the setup needed to actually
  demonstrate horizontal scalability, not just claim it.
- Document local Docker Compose usage in `README.md`.

## Capabilities

### New Capabilities
- `observability`: Actuator health endpoint (including circuit breaker state), Prometheus-formatted
  metrics endpoint, circuit breaker metrics bound to Micrometer.
- `containerized-deployment`: multi-stage Dockerfile producing a runnable image, and a Docker
  Compose topology (Redis + PostgreSQL + 2 app instances) that a reader can start with one command.

### Modified Capabilities
(none — purely additive: new Actuator endpoints and new deployment artifacts, no existing
requirement changes)

## Impact

- `rls-bootstrap`: adds `spring-boot-starter-actuator`, `micrometer-registry-prometheus`,
  `resilience4j-micrometer`; new `CircuitBreakerHealthIndicator` bean; `ResilienceConfig` changes
  from constructing a bare `CircuitBreaker` to going through a `CircuitBreakerRegistry` so its
  metrics can be bound; new `application.yml` Actuator configuration (which endpoints are exposed).
- New repo-root `Dockerfile` and `docker-compose.yml`.
- `README.md`: local Docker Compose instructions.
- No changes to `rls-domain`, existing REST/web controllers, or persistence/Redis adapters beyond
  the `ResilienceConfig` registry change (behaviorally identical — same single global circuit
  breaker, same name, same config).
