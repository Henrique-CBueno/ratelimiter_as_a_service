## Why

The service has never been validated under concurrent load: every existing spec was verified via unit/integration tests or manual single-request checks, never with realistic concurrency. There's no way to state a throughput or latency claim for the rate-limit check endpoint, no repeatable way to compare behavior before/after infrastructure changes (e.g. introducing a load balancer), and no way to confirm the rate-limit strategies actually cap request rates correctly under sustained concurrent traffic rather than just sequential test assertions.

## What Changes

- Add a `load-tests/` directory at the repo root with versioned k6 scripts:
  - `check-rate-limit.js` — the primary scenario: concurrent virtual users across multiple tenants/resources hammering `POST /api/v1/ratelimit/check`, asserting p95 latency and that allowed vs. `429` counts respect the configured limit.
  - `tenant-onboarding.js` — concurrent `POST /api/v1/tenants` creation.
  - `resource-crud.js` — concurrent create/update/delete against `/api/v1/resources`.
  - `web-auth.js` — concurrent login against `/app/login` (session + CSRF flow).
- Each script defines k6 `thresholds` (e.g. `http_req_duration{p(95)}`, `http_req_failed` excluding intentional `429`s) as pass/fail criteria, and a `setup()` function that provisions its own test tenant/resources rather than relying on manual fixtures.
- Scripts are parameterized via environment variables (`BASE_URL`, tenant credentials) so they can run against `mvn spring-boot:run`, the existing two-instance `docker-compose.yml`, or (once `add-load-balancer` lands) the Traefik-fronted endpoint.
- Document k6 usage (install step, `k6 run load-tests/<script>.js`, how to read threshold output) in `README.md`.

**Non-goals:** no CI integration in this round (scripts run locally/on-demand); no results dashboard or long-term metrics storage.

## Capabilities

### New Capabilities
- `load-testing`: k6-based load test scripts and pass/fail thresholds covering the rate-limit check endpoint, tenant onboarding, resource CRUD, and the web login flow, runnable against any deployment topology via a configurable base URL.

### Modified Capabilities
(none — purely additive: new scripts and documentation, no existing requirement changes)

## Impact

- New `load-tests/` directory (k6 scripts), no production code changes.
- `README.md`: new "Load testing" section (prerequisites, how to run, how to interpret thresholds).
- No changes to any Maven module, `Dockerfile`, or `docker-compose.yml`.
