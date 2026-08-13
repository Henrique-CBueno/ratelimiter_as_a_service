## Context

The application is a reactive Spring Boot service whose core value proposition is enforcing rate limits correctly under concurrency (5 strategies: Fixed Window, Sliding Window Log/Counter, Token Bucket, Leaky Bucket/GCRA). Everything verifying that today is either a unit test against the domain logic directly, or a Testcontainers-based `*IT`/E2E test issuing requests sequentially or with small fixed concurrency. There is no tool in the repo capable of generating sustained, configurable concurrent HTTP load, and no existing convention for where such scripts would live (this is the first non-JVM tooling in the repository).

## Goals / Non-Goals

**Goals:**
- Provide repeatable, scriptable load scenarios for the four most important HTTP surfaces: rate-limit check, tenant onboarding, resource CRUD, and web login.
- Make scenarios runnable against any of the deployment topologies the project already supports (`mvn spring-boot:run`, `docker-compose.yml` with one or more `app` instances) via a single `BASE_URL` env var, so the same scripts later validate the `add-load-balancer` change without modification.
- Encode success/failure as k6 `thresholds` so a run has an unambiguous pass/fail exit code, not just eyeballed output.

**Non-Goals:**
- No CI wiring in this change — scripts are run on-demand by a developer, documented in `README.md`.
- No results storage/dashboarding (e.g. k6 Cloud, InfluxDB+Grafana) — console/summary output only.
- No load testing of Actuator/Swagger endpoints — out of scope, not part of the service's core contract.

## Decisions

1. **Tool: k6, standalone binary.** No Node/npm dependency (k6 ships as a single Go binary), scripts written in k6's JS-flavored DSL. Chosen over Gatling to avoid adding a second JVM build module purely for load testing, and over JMeter/Locust for git-friendly plain-text scripts and first-class `thresholds` support.
2. **Location: `load-tests/` at repo root**, not inside any Maven module — load tests exercise the deployed system as a black box across module boundaries (REST + web layers, Redis, Postgres) and aren't part of the Maven reactor build.
3. **Parameterization via env vars** (`BASE_URL`, defaulting to `http://localhost:8080`; test credentials generated in `setup()` rather than hardcoded), so the identical script targets `spring-boot:run`, the two-instance compose topology, or a future Traefik-fronted single entrypoint.
4. **Self-provisioning via k6 `setup()`**: each script creates its own tenant(s)/resource(s) via the real API at the start of the run and does not depend on manually seeded fixtures or a fixed database state — keeps scripts runnable repeatedly without manual cleanup between runs.
5. **Thresholds distinguish expected `429`s from real failures**: `http_req_failed` is scoped to exclude expected `429 Too Many Requests` responses (checked via a custom k6 `Trend`/tag rather than the default `http_req_failed` metric, which only tracks network/5xx-level failure by default in k6 — `429` isn't automatically flagged, so this is mostly about *asserting* the 429 rate lands within an expected band, not excluding it from a metric that wouldn't have counted it anyway).

## Risks / Trade-offs

- [Risk] Load test results are only as meaningful as the machine they run on (noisy neighbor effects, laptop vs. CI hardware) → Mitigation: document this caveat in `README.md`; treat thresholds as regression guards, not absolute SLAs.
- [Risk] Running `tenant-onboarding.js` or `resource-crud.js` repeatedly against a persistent Postgres will accumulate rows over time → Mitigation: document that these scripts are intended for disposable/local Postgres state (e.g. `docker compose down -v` between runs), not a shared long-lived database.
- [Risk] k6 must be installed separately (not bundled via Maven/Docker) → Mitigation: document install instructions (single binary, no package manager required) in `README.md`.
