## ADDED Requirements

### Requirement: Rate-limit evaluation port
The system SHALL expose a `RateLimitEvaluationPort` in `rls-application` with a method that
evaluates a `RateLimitKey` against a `StrategyType` and `Quota`, returning a reactive
`RateLimitDecision`, independent of any specific storage technology.

#### Scenario: Port is implemented without leaking storage details
- **WHEN** `rls-application` code depends on `RateLimitEvaluationPort`
- **THEN** it does so without importing any Redis-specific type (the port's method signature uses
  only domain types: `RateLimitKey`, `StrategyType`, `Quota`, and `Mono<RateLimitDecision>`)

### Requirement: Atomic evaluation via Redis Lua scripts
For each of the five strategy types, the Redis adapter SHALL evaluate and mutate the relevant
counter state as a single atomic Lua (`EVAL`) operation, so no other concurrent evaluation against
the same key can observe or apply a partial update.

#### Scenario: Concurrent requests against the same key never exceed the limit
- **WHEN** a number of requests greater than the configured `limit` are issued concurrently against
  the same `RateLimitKey` and `Quota`
- **THEN** exactly `limit` of them are evaluated as allowed and the remainder are evaluated as
  denied, regardless of the number of concurrent callers or application instances involved

### Requirement: Redis is the authoritative clock
Every Lua script SHALL determine the current time via `redis.call('TIME')` inside the script
itself; the evaluation port SHALL NOT pass an application-supplied timestamp into the script as the
basis for window/refill calculations.

#### Scenario: Decisions are consistent regardless of caller clock skew
- **WHEN** two evaluation calls for the same key are issued by callers with different local system
  clocks
- **THEN** both evaluations are computed against the same Redis-observed time, producing consistent
  window/refill state regardless of the callers' local clock values

### Requirement: Behavioral parity with the domain strategies
For each strategy type, the Redis-backed evaluation SHALL produce the same `allowed` and
`remaining` outcomes as the corresponding pure `RateLimitStrategy` implementation from the
`rate-limit-strategy-evaluation` capability, when driven through an identical deterministic
sequence of quota and time inputs.

#### Scenario: Lua script matches the domain strategy for an identical call sequence
- **WHEN** the same deterministic sequence of `(quota, now)` evaluations is applied first to a
  strategy's Lua script (via Redis) and then to its corresponding pure domain `RateLimitStrategy`
  implementation, starting from equivalent empty state
- **THEN** every evaluation in the sequence produces the same `allowed` result and the same
  `remaining` value from both implementations

### Requirement: Strategy-scoped key isolation
The Redis key used for a given `RateLimitKey` SHALL include the strategy type, so that changing a
resource's configured strategy never causes an evaluation to read a Redis value written by a
different strategy's data structure.

#### Scenario: Switching strategies does not error or reuse incompatible state
- **WHEN** a resource previously evaluated under one strategy type is evaluated again under a
  different strategy type for the same tenant, resource, and client IP
- **THEN** the evaluation succeeds without a Redis type error and starts from that new strategy's
  own empty state, independent of any state left behind by the previous strategy

### Requirement: Decision reply mapping
The adapter SHALL map each Lua script's reply into a complete `RateLimitDecision`, including
`allowed`, `limit`, `remaining`, `resetAt`, and — when denied — a non-null `retryAfter`.

#### Scenario: Denied evaluation carries retry guidance
- **WHEN** the Redis adapter evaluates a request that the underlying strategy denies
- **THEN** the resulting `RateLimitDecision` has `allowed = false` and a non-null `retryAfter`
