# observability Specification

## Purpose
TBD - created by syncing change observability-docker. Update Purpose after archive.

## Requirements

### Requirement: Health endpoint reports dependency status
The system SHALL expose `GET /actuator/health` reporting the status of its Redis connection, its
PostgreSQL/R2DBC connection, and the rate-limit evaluation circuit breaker.

#### Scenario: Healthy dependencies report UP
- **WHEN** `GET /actuator/health` is called while Redis and PostgreSQL are reachable and the
  circuit breaker is closed
- **THEN** the response is `200 OK` with an overall status of `UP`

### Requirement: Circuit breaker state is reflected in health, not reported as a hard failure
The system SHALL report the circuit breaker's `OPEN` state as a distinct `DEGRADED` status, not
`DOWN`, since the application continues serving requests via its configured fallback policy while
the circuit is open.

#### Scenario: Open circuit reports DEGRADED, not DOWN
- **WHEN** `GET /actuator/health` is called while the rate-limit evaluation circuit breaker is open
- **THEN** the response reflects a `DEGRADED` status for the circuit breaker component, and the
  overall aggregated status is not `UP`

#### Scenario: Closed or half-open circuit reports UP
- **WHEN** `GET /actuator/health` is called while the circuit breaker is closed or half-open
- **THEN** the response reflects an `UP` status for the circuit breaker component

### Requirement: Metrics are exposed in Prometheus format
The system SHALL expose `GET /actuator/prometheus` with Micrometer-collected metrics, including the
rate-limit evaluation circuit breaker's call outcomes and state transitions.

#### Scenario: Circuit breaker metrics are present
- **WHEN** `GET /actuator/prometheus` is called after at least one rate-limit check has been
  evaluated
- **THEN** the response includes Resilience4j circuit breaker metrics for the
  `redis-rate-limit` instance

### Requirement: Actuator exposure is limited to health and metrics
The system SHALL expose only the `health` and `prometheus` Actuator endpoints, not the full
Actuator endpoint set.

#### Scenario: Unlisted Actuator endpoints are not reachable
- **WHEN** an Actuator endpoint other than `health` or `prometheus` (for example `env` or `beans`)
  is requested
- **THEN** the response is `404 Not Found`
