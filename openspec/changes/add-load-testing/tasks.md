## 1. Setup

- [ ] 1.1 Create `load-tests/` directory at repo root
- [ ] 1.2 Add a shared `load-tests/lib/http.js` helper (or similar) for `BASE_URL` resolution from env, common headers, and tenant/resource setup helpers reused across scripts

## 2. Rate-limit check load test

- [ ] 2.1 Write `load-tests/check-rate-limit.js`: `setup()` provisions a test tenant + resource with a known strategy/limit via the real API
- [ ] 2.2 Implement the VU logic issuing concurrent `POST /api/v1/ratelimit/check` requests exceeding the configured limit
- [ ] 2.3 Add thresholds: p95 latency bound, and a check asserting the allowed-request count does not exceed the configured limit

## 3. Tenant onboarding load test

- [ ] 3.1 Write `load-tests/tenant-onboarding.js` issuing concurrent `POST /api/v1/tenants` with unique payloads per VU/iteration
- [ ] 3.2 Add latency and error-rate thresholds

## 4. Resource CRUD load test

- [ ] 4.1 Write `load-tests/resource-crud.js`: `setup()` provisions a tenant, VUs concurrently create/update/delete resources under it
- [ ] 4.2 Add latency and error-rate thresholds

## 5. Web login load test

- [ ] 5.1 Write `load-tests/web-auth.js`: `setup()` registers a test web user via `/app/register`
- [ ] 5.2 Implement VU logic performing `GET /app/login` (to fetch the CSRF token) followed by `POST /app/login` concurrently
- [ ] 5.3 Add latency and error-rate thresholds

## 6. Documentation

- [ ] 6.1 Add a "Load testing" section to `README.md`: k6 install instructions, how to run each script, `BASE_URL` usage, how to read threshold pass/fail output
- [ ] 6.2 Note the disposable-database caveat for `tenant-onboarding.js`/`resource-crud.js` (recommend `docker compose down -v` between repeated runs)
