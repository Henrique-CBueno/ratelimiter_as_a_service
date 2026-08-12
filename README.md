# Rate Limiter as a Service

Rate Limiter as a Service (RLS) is a reactive Java application that lets tenants register their
own rate-limiting policies and check, per request, whether a given resource + client IP is
authorized under that policy.

Each tenant can configure, per resource, one of five strategies:

- Fixed Window
- Sliding Window Log
- Sliding Window Counter
- Token Bucket
- Leaky Bucket (GCRA)

with a custom limit and time window.

The service is designed to be stateless and horizontally scalable: all rate-limit counters live in
Redis (evaluated atomically via Lua scripts to avoid race conditions across concurrent instances),
tenant/resource configuration lives in PostgreSQL, and a circuit breaker protects the hot path
against Redis unavailability with a per-tenant configurable fallback policy (fail-open /
fail-closed). A server-rendered Thymeleaf front end provides tenant self-onboarding, session-based
login, and a resource-management dashboard, alongside the REST API.

## Architecture

The project follows hexagonal architecture (ports & adapters), Domain-Driven Design, and
Test-Driven Development. See `docs/architecture-plan.md` for the full architectural design and
`openspec/` for the change-by-change specifications driving the implementation.

Modules (added incrementally, one per spec):

- `rls-domain` — pure Java domain model (aggregates, value objects, the five rate-limit strategies)
- `rls-application` — use cases and ports (`RateLimitEvaluationPort`)
- `rls-adapter-redis` — distributed counters via Redis + Lua (one atomic `EVAL` script per
  strategy); integration tests use Testcontainers, so **Docker must be running** to execute them
- `rls-adapter-persistence` — tenant/resource persistence via PostgreSQL + R2DBC, schema managed
  by Flyway; integration tests use Testcontainers (PostgreSQL), so **Docker must be running**
- `rls-adapter-resilience` — circuit breaker decorator (Resilience4j), the single global circuit
  breaker guarding `RateLimitEvaluationPort`
- `rls-adapter-rest` — reactive REST API (tenant onboarding, resource management, rate-limit check,
  API key auth via `Authorization: Bearer`, OpenAPI/Swagger UI)
- `rls-adapter-web` — server-rendered Thymeleaf front end under `/app/**`: self-registration
  (`/app/register`), session-based login/logout (`/app/login`, Spring Session backed by Redis so
  sessions stay stateless across instances), and a resource-management dashboard
  (`/app/resources`, `/app/settings`) that calls the same `rls-application` use cases the REST API
  uses. Independent of the REST API's `Authorization: Bearer` auth — see design decision 3 in
  `openspec/changes/archive/2026-08-12-thymeleaf-frontend/design.md`
- `rls-bootstrap` — Spring Boot application assembly (composition root wiring every adapter and use
  case together, plus `application.yml`); also exposes Actuator health (`/actuator/health`,
  including the rate-limit circuit breaker's own state) and Prometheus metrics
  (`/actuator/prometheus`)

## Git workflow

This repository follows the git-flow branching model:

- `main` — always reflects production-ready code
- `develop` — integration branch for completed features
- `feature/<name>` — one branch per OpenSpec change, branched from `develop`, merged back into
  `develop` when the change's tasks are complete
- `release/<version>` — release stabilization branches, branched from `develop`, merged into both
  `main` and `develop`
- `hotfix/<name>` — urgent fixes branched from `main`, merged into both `main` and `develop`

## Building

Requires JDK 21+, Maven, and a running Docker daemon — `rls-adapter-redis`'s and
`rls-adapter-persistence`'s integration tests start real `redis:7-alpine` and `postgres:16-alpine`
containers via Testcontainers. From the repository root:

```
mvn verify
```

This compiles every module and runs the full test suite: unit tests via Surefire (`*Test.java`,
no external dependencies) and integration tests via Failsafe (`*IT.java`, needs Docker). A JaCoCo
coverage report is generated per module at `target/site/jacoco/index.html`.

To build/test a single module (and the modules it depends on), use `-pl` with `-am`, e.g.:

```
mvn -pl rls-adapter-persistence -am verify
```

Modules existing so far: `rls-domain`, `rls-application`, `rls-adapter-redis`,
`rls-adapter-persistence`, `rls-adapter-resilience`, `rls-adapter-rest`, `rls-adapter-web`,
`rls-bootstrap`. Observability and Docker Compose are added by a later OpenSpec change.

## Running locally

`rls-bootstrap` is the runnable application. It needs a real Redis and PostgreSQL — not just the
ephemeral Testcontainers instances the test suite spins up — so start them first, e.g.:

```
docker run -d --name rls-redis -p 6379:6379 redis:7-alpine
docker run -d --name rls-postgres -p 5432:5432 -e POSTGRES_USER=rls -e POSTGRES_PASSWORD=rls -e POSTGRES_DB=rls postgres:16-alpine
```

Then run the application from the repository root:

```
mvn -pl rls-bootstrap -am spring-boot:run
```

Connection details default to `localhost` and the ports above; override via the `REDIS_HOST`,
`REDIS_PORT`, `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DATABASE`, `POSTGRES_USERNAME`, and
`POSTGRES_PASSWORD` environment variables (see `rls-bootstrap/src/main/resources/application.yml`).
Flyway migrates the schema automatically on startup. Once running:

- `POST http://localhost:8080/api/v1/tenants` — register a tenant via the REST API, get back an
  API token
- `http://localhost:8080/app/register` — register a tenant via the browser dashboard instead
- `http://localhost:8080/app/login` — log in to the dashboard (session-based, separate from the
  REST API's bearer token auth)
- `http://localhost:8080/swagger-ui.html` — interactive API docs
- `http://localhost:8080/v3/api-docs` — raw OpenAPI spec
- `http://localhost:8080/actuator/health` — health, including the rate-limit circuit breaker's own
  state (`DEGRADED`, not `DOWN`, while it's open — the app is still serving traffic via fallback)
- `http://localhost:8080/actuator/prometheus` — Prometheus-formatted metrics

## Running with Docker Compose

The repository root has a multi-stage `Dockerfile` and a `docker-compose.yml` that starts Redis,
PostgreSQL, and **two** application instances sharing them — the setup used to actually
demonstrate the stateless/horizontal-scalability design, not just claim it:

```
docker compose up --build
```

- App instance 1: `http://localhost:8081`
- App instance 2: `http://localhost:8082`

Both instances enforce rate limits through the same Redis-backed state, so alternating requests
between the two ports for the same tenant/resource/client IP combination is enforced exactly as if
every request had gone to a single instance. `docker compose down` tears the stack down.
