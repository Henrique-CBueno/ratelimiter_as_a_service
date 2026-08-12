## Why

The domain core (spec 1) defines the five rate-limit strategies as pure, synchronous, in-memory
functions — deliberately free of I/O so they can be TDD'd in isolation. But the product requirement
is a **stateless, horizontally scalable** service: multiple instances must share and atomically
mutate the same counters without race conditions. That requires a real distributed store. This
change adds the Redis-backed outbound adapter that makes rate-limit evaluation actually work across
concurrent instances, using Lua scripts (`EVAL`) so each strategy's read-check-write sequence is
atomic inside Redis itself, and treats the pure domain strategies from spec 1 as the executable
specification the Lua scripts must match.

## What Changes

- Add `RateLimitEvaluationPort` to `rls-application`: a reactive outbound port
  (`Mono<RateLimitDecision> evaluate(RateLimitKey key, StrategyType strategyType, Quota quota)`)
  that the future REST adapter (spec 4) will depend on, decoupled from any specific store.
- Add the `rls-adapter-redis` Maven module (Spring Data Redis Reactive / Lettuce) implementing
  `RateLimitEvaluationPort`.
- Add five Lua scripts (`EVAL`), one per strategy, each performing its algorithm's read-check-write
  atomically inside Redis: Fixed Window (`INCR`/`PEXPIRE`), Sliding Window Log (`ZSET`), Sliding
  Window Counter (two `STRING` buckets), Token Bucket (`HASH`), Leaky Bucket/GCRA (`STRING` TAT).
  Every script reads the current time via `redis.call('TIME')` inside Lua — never from the
  application — so a stateless, multi-instance deployment never disagrees on "now" across
  instances with clock skew.
- Add a strategy-to-script registry in the adapter mapping `StrategyType` to its Lua script,
  mirroring the domain's `StrategyRegistry` from spec 1.
- Add a Redis key naming convention: `rl:{tenantId}:{resourceId}:{clientIp}:{strategyCode}`, so
  switching a resource's strategy never causes a `WRONGTYPE` error against a key holding a
  different Redis data structure — the old key simply expires via TTL.
- Add concurrency tests (Testcontainers + real Redis) that fire many parallel requests at the same
  key and assert exactly `limit` are allowed — the actual proof that the adapter prevents race
  conditions, which no in-memory domain test can demonstrate.
- Add parity tests that drive the same deterministic sequence of calls through both a Lua script
  and its corresponding pure domain strategy (from spec 1) and assert identical decisions, so the
  domain strategies keep serving as an executable specification for the Redis implementation.

## Capabilities

### New Capabilities
- `distributed-rate-limit-evaluation`: the behavior of evaluating rate-limit decisions against a
  shared Redis-backed store — atomicity under concurrency, parity with the domain strategy
  algorithms, Redis-sourced clock, and key isolation across strategy changes.

### Modified Capabilities
(none — `rate-limit-strategy-evaluation` from spec 1 is unchanged; this change adds a new
infrastructure-facing capability that must behave consistently with it, not a change to its
requirements)

## Impact

- **New dependency**: Redis (via Spring Data Redis Reactive / Lettuce), required at runtime from
  this spec onward; Testcontainers' Redis module required for the new module's test suite.
- **New Maven module**: `rls-adapter-redis`, depending on `rls-application` and `rls-domain` (the
  latter only for its test-scope parity tests against the pure strategies).
- **`rls-application`** gains its first port (`RateLimitEvaluationPort`) — still no Spring/Redis
  dependency there, only the `reactor-core` contract already present since spec 1.
- No REST endpoints, persistence, or circuit breaker yet — this adapter is exercised only through
  its own test suite until spec 4 wires it behind the REST API. A local Redis instance
  (Testcontainers) is required to run this module's tests; no `docker-compose.yml` for manual
  running is added yet (that's spec 6's scope).
