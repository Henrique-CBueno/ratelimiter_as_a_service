# resilient-evaluation-fallback Specification

## Purpose
TBD - created by syncing change rest-api. Update Purpose after archive.

## Requirements

### Requirement: A single global circuit breaker guards rate-limit evaluation
The system SHALL protect calls to the Redis-backed `RateLimitEvaluationPort` with one shared
circuit breaker instance, independent of tenant or resource, that opens when the evaluation
dependency's failure rate exceeds its configured threshold.

#### Scenario: Repeated evaluation failures open the circuit
- **WHEN** calls to the underlying rate-limit evaluation dependency fail repeatedly beyond the
  circuit breaker's configured threshold
- **THEN** the circuit breaker transitions to open, and subsequent evaluation calls fail fast
  without attempting to reach the dependency

### Requirement: Fail-open fallback allows requests while the circuit is open
When a resource or its tenant's default fallback policy is `FAIL_OPEN`, the system SHALL treat an
open-circuit or failed evaluation as allowed, marking the decision as degraded.

#### Scenario: Fail-open resource is allowed during an open circuit
- **WHEN** a check request is evaluated for a resource whose effective fallback policy is
  `FAIL_OPEN` while the evaluation circuit breaker is open
- **THEN** the resulting decision has `allowed = true` and is marked as degraded

### Requirement: Fail-closed fallback denies requests while the circuit is open
When a resource or its tenant's default fallback policy is `FAIL_CLOSED`, the system SHALL treat an
open-circuit or failed evaluation as denied, marking the decision as degraded.

#### Scenario: Fail-closed resource is denied during an open circuit
- **WHEN** a check request is evaluated for a resource whose effective fallback policy is
  `FAIL_CLOSED` while the evaluation circuit breaker is open
- **THEN** the resulting decision has `allowed = false` and is marked as degraded

### Requirement: Effective fallback policy follows resource-then-tenant precedence
The system SHALL resolve the fallback policy applied during an open circuit using the resource's
own policy when set, falling back to its owning tenant's default policy when the resource does not
define one.

#### Scenario: Resource-level policy overrides the tenant default
- **WHEN** a resource has its own fallback policy configured, different from its tenant's default
  fallback policy, and the evaluation circuit breaker is open
- **THEN** the resource's own fallback policy is applied, not the tenant's default
