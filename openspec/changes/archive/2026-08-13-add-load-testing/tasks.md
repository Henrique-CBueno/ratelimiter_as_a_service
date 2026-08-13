## 1. Setup

- [x] 1.1 Create `load-tests/` directory at repo root
- [x] 1.2 Add a shared `load-tests/lib/http.js` helper (or similar) for `BASE_URL` resolution from env, common headers, and tenant/resource setup helpers reused across scripts

## 2. Rate-limit check load test

- [x] 2.1 Write `load-tests/check-rate-limit.js`: `setup()` provisions a test tenant + resource with a known strategy/limit via the real API
- [x] 2.2 Implement the VU logic issuing concurrent `POST /api/v1/ratelimit/check` requests exceeding the configured limit
- [x] 2.3 Add thresholds: p95 latency bound, and a check asserting the allowed-request count does not exceed the configured limit

## 3. Tenant onboarding load test

- [x] 3.1 Write `load-tests/tenant-onboarding.js` issuing concurrent `POST /api/v1/tenants` with unique payloads per VU/iteration
- [x] 3.2 Add latency and error-rate thresholds

## 4. Resource CRUD load test

- [x] 4.1 Write `load-tests/resource-crud.js`: `setup()` provisions a tenant, VUs concurrently create/update/delete resources under it
- [x] 4.2 Add latency and error-rate thresholds — required fixing a real production bug found while verifying this script: `ApiTokenAuthenticationWebFilter` always overwrote successful no-body (204) responses with 401 (see `rls-adapter-rest/.../auth/ApiTokenAuthenticationWebFilter.java`), which made DELETE always fail. Fixed and covered by a new regression test in `ApiTokenAuthenticationWebFilterTest`.

## 5. Web login load test

- [x] 5.1 Write `load-tests/web-auth.js`: `setup()` registers a test web user via `/app/register`
- [x] 5.2 Implement VU logic performing `GET /app/login` (to fetch the CSRF token) followed by `POST /app/login` concurrently
- [x] 5.3 Add latency and error-rate thresholds

## 6. Documentation

- [x] 6.1 Add a "Load testing" section to `README.md`: k6 install instructions, how to run each script, `BASE_URL` usage, how to read threshold pass/fail output
- [x] 6.2 Note the disposable-database caveat for `tenant-onboarding.js`/`resource-crud.js` (recommend `docker compose down -v` between repeated runs)
