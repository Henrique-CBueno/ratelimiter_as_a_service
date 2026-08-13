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
PostgreSQL, a [Traefik](https://traefik.io) reverse proxy, and a scalable `app` service — the setup
used to actually demonstrate the stateless/horizontal-scalability design, not just claim it:

```
docker compose up --build --scale app=2
```

- App (via Traefik): `http://localhost`
- Traefik dashboard: `http://localhost:8080` (local/dev only — no auth in front of it)

Traefik discovers every `app` replica automatically via Docker labels (no static config to update)
and load-balances across them round-robin, routing only to instances whose `/actuator/health`
currently reports healthy. Individual instances are **not** reachable on their own host port — the
container publishes no port at all, so `http://localhost` is the only way in. Scale up or down at
any time without touching any config file:

```
docker compose up -d --scale app=4
```

All instances enforce rate limits through the same Redis-backed state, so requests distributed
across them for the same tenant/resource/client IP combination are enforced exactly as if every
request had gone to a single instance. `docker compose down` tears the stack down.

## Metrics dashboard

The same `docker compose up` also starts Prometheus and a pre-provisioned Grafana — no manual setup
needed, the datasource and dashboard are already there on first start:

- Grafana: `http://localhost:3000` (default login `admin`/`admin`, local/dev only) — open the
  **Load Distribution** dashboard for total request rate, request rate per `app` instance, and each
  instance's percentage share of total load
- Prometheus: `http://localhost:9090` — browse raw metrics or check `/targets` to see every
  currently-scraped `app` instance and Traefik

Prometheus discovers `app` replicas the same way Traefik does — automatically, via Docker — so
scaling with `--scale app=N` shows up on the dashboard without touching any config.

## Load testing

`load-tests/` has [k6](https://k6.io) scripts covering the rate-limit check endpoint, tenant
onboarding, resource CRUD, and the web login flow. Install k6 (a single binary, no package manager
required — see the [installation docs](https://grafana.com/docs/k6/latest/set-up/install-k6/)),
then run any script against a running instance of the app:

```
BASE_URL=http://localhost:8080 k6 run load-tests/check-rate-limit.js
BASE_URL=http://localhost:8080 k6 run load-tests/tenant-onboarding.js
BASE_URL=http://localhost:8080 k6 run load-tests/resource-crud.js
BASE_URL=http://localhost:8080 k6 run load-tests/web-auth.js
```

`BASE_URL` defaults to `http://localhost:8080`; point it at the Docker Compose topology (e.g.
`http://localhost`, via Traefik) or any other reachable instance. Each script provisions its own
test tenant/resources via the real API — no manual setup needed.

Each run ends with a **THRESHOLDS** section: a `✓`/`✗` per threshold tells you pass/fail at a
glance, and the run's process exits non-zero if any threshold failed (useful for scripting). A few
things worth knowing when reading the output:

- `check-rate-limit.js` deliberately exceeds the configured limit — seeing `429`s in the results is
  the expected, correct outcome, not a failure. The threshold on `http_req_failed` only flags
  genuine infra errors (network failures, 5xx), since `429` is explicitly excluded from that
  classification for this script.
- `tenant-onboarding.js` and `resource-crud.js` create real rows in PostgreSQL on every run and
  don't clean up after themselves — they're meant for a disposable/local database. Run
  `docker compose down -v` between repeated runs if you want a clean slate (the `-v` also drops the
  Postgres volume, not just the containers).
