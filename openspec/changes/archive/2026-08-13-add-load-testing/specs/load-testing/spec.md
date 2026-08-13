## ADDED Requirements

### Requirement: Rate-limit check endpoint has a load test scenario
The system SHALL provide a k6 script that drives concurrent virtual users against `POST /api/v1/ratelimit/check` across multiple tenants and resources, asserting latency and correctness thresholds.

#### Scenario: Running the check-endpoint load test reports pass/fail
- **WHEN** `k6 run load-tests/check-rate-limit.js` is executed against a running instance of the application
- **THEN** the run provisions its own test tenant(s) and resource(s), issues concurrent requests exceeding the configured limit, and exits non-zero if p95 latency or the allowed-vs-limited ratio violates the script's configured thresholds

### Requirement: Tenant onboarding has a load test scenario
The system SHALL provide a k6 script that drives concurrent `POST /api/v1/tenants` requests to validate onboarding behavior under load.

#### Scenario: Running the onboarding load test reports pass/fail
- **WHEN** `k6 run load-tests/tenant-onboarding.js` is executed against a running instance of the application
- **THEN** the run creates multiple tenants concurrently and exits non-zero if request latency or error-rate thresholds are violated

### Requirement: Resource CRUD has a load test scenario
The system SHALL provide a k6 script that drives concurrent create/update/delete requests against `/api/v1/resources` to validate resource management under load.

#### Scenario: Running the resource CRUD load test reports pass/fail
- **WHEN** `k6 run load-tests/resource-crud.js` is executed against a running instance of the application
- **THEN** the run performs concurrent create, update, and delete operations against resources and exits non-zero if latency or error-rate thresholds are violated

### Requirement: Web login flow has a load test scenario
The system SHALL provide a k6 script that drives concurrent logins against the Thymeleaf `/app/login` flow, including CSRF token handling, to validate the web authentication path under load.

#### Scenario: Running the web-auth load test reports pass/fail
- **WHEN** `k6 run load-tests/web-auth.js` is executed against a running instance of the application
- **THEN** the run performs concurrent session-based logins and exits non-zero if latency or error-rate thresholds are violated

### Requirement: Load test scripts target a configurable base URL
The system SHALL allow every load test script's target host to be configured via an environment variable, without editing the script, so the same script can run against any supported deployment topology.

#### Scenario: Overriding the target host
- **WHEN** a load test script is run with the `BASE_URL` environment variable set to a non-default host (e.g. a Docker Compose or load-balanced endpoint)
- **THEN** all requests issued by the script target that host instead of the default `http://localhost:8080`
