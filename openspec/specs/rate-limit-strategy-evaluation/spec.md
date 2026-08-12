# rate-limit-strategy-evaluation Specification

## Purpose
TBD - created by archiving change setup-domain-core. Update Purpose after archive.

## Requirements

### Requirement: Fixed Window strategy evaluation
The system SHALL provide a pure, synchronous `FixedWindowStrategy` that, given the current window
state (count, window start), a `Quota` (limit, window duration) and the current instant, returns a
`RateLimitDecision` and the updated state, without performing any I/O.

#### Scenario: Request allowed within the window
- **WHEN** the current count in the active window is below the configured limit
- **THEN** the strategy returns `allowed = true`, increments the count, and `remaining = limit - newCount`

#### Scenario: Request denied at the limit
- **WHEN** the current count in the active window has already reached the configured limit
- **THEN** the strategy returns `allowed = false` and `remaining = 0`, without incrementing the count

#### Scenario: Window rollover resets the count
- **WHEN** the current instant falls outside the active window (a new window has started)
- **THEN** the strategy starts a new window with count reset to zero before evaluating the request

### Requirement: Sliding Window Log strategy evaluation
The system SHALL provide a pure, synchronous `SlidingWindowLogStrategy` that evaluates requests
against a log of timestamps within a trailing window of the configured duration, without any I/O.

#### Scenario: Request allowed when the trailing window has capacity
- **WHEN** the number of recorded timestamps within `[now - window, now]` is below the configured limit
- **THEN** the strategy returns `allowed = true` and records the new timestamp in the state

#### Scenario: Request denied when the trailing window is full
- **WHEN** the number of recorded timestamps within `[now - window, now]` equals the configured limit
- **THEN** the strategy returns `allowed = false` and does not record the new timestamp

#### Scenario: Expired timestamps are purged before counting
- **WHEN** the state contains timestamps older than `now - window`
- **THEN** the strategy excludes those expired timestamps from the count before deciding

### Requirement: Sliding Window Counter strategy evaluation
The system SHALL provide a pure, synchronous `SlidingWindowCounterStrategy` that estimates the
request count using a weighted combination of the current and previous fixed-window counters,
without any I/O.

#### Scenario: Request allowed under the weighted estimate
- **WHEN** `previousWindowCount * (1 - elapsedFraction) + currentWindowCount` is below the configured limit
- **THEN** the strategy returns `allowed = true` and increments the current window counter

#### Scenario: Request denied when the weighted estimate reaches the limit
- **WHEN** `previousWindowCount * (1 - elapsedFraction) + currentWindowCount` is greater than or equal to the configured limit
- **THEN** the strategy returns `allowed = false` without incrementing the current window counter

### Requirement: Token Bucket strategy evaluation
The system SHALL provide a pure, synchronous `TokenBucketStrategy` that refills tokens
proportionally to elapsed time since the last refill and consumes one token per allowed request,
without any I/O.

#### Scenario: Request allowed when at least one token is available after refill
- **WHEN** `min(capacity, tokens + elapsed_seconds * refillRate) >= 1`
- **THEN** the strategy returns `allowed = true`, consumes one token, and updates `lastRefill` to the current instant

#### Scenario: Request denied when no tokens are available after refill
- **WHEN** `min(capacity, tokens + elapsed_seconds * refillRate) < 1`
- **THEN** the strategy returns `allowed = false` and does not consume a token, but still updates the refilled token count and `lastRefill`

### Requirement: Leaky Bucket strategy evaluation
The system SHALL provide a pure, synchronous `LeakyBucketStrategy` implementing GCRA (Generic Cell
Rate Algorithm) semantics, tracking only a theoretical arrival time (TAT), without any I/O.

#### Scenario: Request allowed when it arrives at or after the allowed time
- **WHEN** `now >= TAT - burstCapacity * emissionInterval`
- **THEN** the strategy returns `allowed = true` and sets the new `TAT = max(TAT, now) + emissionInterval`

#### Scenario: Request denied when it arrives before the allowed time
- **WHEN** `now < TAT - burstCapacity * emissionInterval`
- **THEN** the strategy returns `allowed = false` and leaves the stored `TAT` unchanged

### Requirement: Strategy registry resolution
The system SHALL provide a `StrategyRegistry` that resolves a `StrategyType` value to its
corresponding `RateLimitStrategy` implementation.

#### Scenario: Known strategy type resolves to its implementation
- **WHEN** `StrategyRegistry.resolve(strategyType)` is called with one of the five supported `StrategyType` values
- **THEN** it returns the matching `RateLimitStrategy` implementation instance

#### Scenario: Unknown strategy type is rejected
- **WHEN** `StrategyRegistry.resolve(strategyType)` is called with a `StrategyType` value that has no registered implementation
- **THEN** the registry throws a domain exception identifying the unsupported strategy type

### Requirement: Rate limit decision metadata
Every `RateLimitDecision` produced by any strategy SHALL include `allowed`, `limit`, `remaining`,
and `resetAt`; when `allowed` is `false`, it SHALL also include a `retryAfter` duration.

#### Scenario: Denied decision includes retry-after guidance
- **WHEN** a strategy evaluates a request and determines `allowed = false`
- **THEN** the resulting `RateLimitDecision` has a non-null `retryAfter` indicating when capacity is expected to be available again
