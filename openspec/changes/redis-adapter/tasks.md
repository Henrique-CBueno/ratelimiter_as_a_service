## 1. Setup

- [ ] 1.1 Create `feature/redis-adapter` branch from `develop`
- [ ] 1.2 Add `rls-adapter-redis` module to the parent `pom.xml`; add Spring Data Redis Reactive
      (Lettuce) as a main dependency and the Testcontainers Redis module as a test dependency,
      depending on `rls-application` (main) and `rls-domain` (test scope, for parity tests)
- [ ] 1.3 Verify `mvn -pl rls-adapter-redis -am compile` succeeds with the empty module skeleton

## 2. RateLimitEvaluationPort (rls-application)

- [ ] 2.1 Define `RateLimitEvaluationPort` interface in `rls-application`
      (`Mono<RateLimitDecision> evaluate(RateLimitKey key, StrategyType strategyType, Quota quota)`),
      using only domain types
- [ ] 2.2 Verify `rls-application` still compiles with zero Redis-specific imports

## 3. Test infrastructure

- [ ] 3.1 Add a Testcontainers Redis test support base (container lifecycle, reactive connection
      factory) in `rls-adapter-redis` test scope
- [ ] 3.2 Write a smoke test confirming the Testcontainers Redis connection and `EVAL` work end to
      end (e.g. a trivial script)

## 4. Fixed Window strategy script

- [ ] 4.1 Write `fixed_window.lua` (`INCR`/`PEXPIRE`, reading time via `redis.call('TIME')`),
      returning `{allowed, limit, remaining, reset_at_ms, retry_after_ms}`
- [ ] 4.2 Wire the script into the adapter (load as a resource, invoke via `EVAL`, map the reply to
      `RateLimitDecision`)
- [ ] 4.3 Write a Testcontainers test: allow under limit, deny at limit, window rollover — mirroring
      `FixedWindowStrategyTest` from spec 1

## 5. Sliding Window Log strategy script

- [ ] 5.1 Write `sliding_window_log.lua` (`ZSET`: `ZREMRANGEBYSCORE` + `ZCARD` + conditional `ZADD`)
- [ ] 5.2 Wire the script into the adapter
- [ ] 5.3 Write a Testcontainers test: allow with capacity, deny when full, expired entries purged

## 6. Sliding Window Counter strategy script

- [ ] 6.1 Write `sliding_window_counter.lua` (two `STRING` buckets, weighted estimate)
- [ ] 6.2 Wire the script into the adapter
- [ ] 6.3 Write a Testcontainers test: allow under weighted estimate, deny at estimate, window
      rollover shifting current into previous

## 7. Token Bucket strategy script

- [ ] 7.1 Write `token_bucket.lua` (`HASH` with `tokens`/`last_refill`, proportional refill)
- [ ] 7.2 Wire the script into the adapter
- [ ] 7.3 Write a Testcontainers test: allow with tokens available, deny when empty, refill
      proportional to elapsed time, refill never exceeds capacity

## 8. Leaky Bucket (GCRA) strategy script

- [ ] 8.1 Write `leaky_bucket.lua` (`STRING` TAT, GCRA math) using
      `tau = (burstCapacity - 1) * emissionInterval` — the exact formula from the spec 1 domain
      implementation (spec 1's own tests caught and fixed an off-by-one here; the script must not
      reintroduce it)
- [ ] 8.2 Wire the script into the adapter
- [ ] 8.3 Write a Testcontainers test: allow at/after the allowed boundary, deny before it, TAT
      persisted only on allow

## 9. Key naming, strategy dispatch, and port wiring

- [ ] 9.1 Implement the Redis key builder following `rl:{tenantId}:{resourceId}:{clientIp}:{strategyCode}`
- [ ] 9.2 Implement the strategy-to-script dispatch in the adapter's `RateLimitEvaluationPort`
      implementation, selecting the right script by `StrategyType`
- [ ] 9.3 Write a test confirming that evaluating the same tenant/resource/IP under a different
      strategy type does not raise a Redis type error and starts from fresh state

## 10. Concurrency proof (atomicity)

- [ ] 10.1 Write a parameterized Testcontainers test, run for all five strategies, that fires more
      concurrent requests than `limit` against the same key and asserts exactly `limit` are allowed

## 11. Parity with the domain strategies

- [ ] 11.1 Build a shared test harness that drives an identical deterministic sequence of
      `(quota, now)` calls through a Lua script (real Redis) and the corresponding pure
      `RateLimitStrategy` from `rls-domain`, asserting identical `allowed`/`remaining` at each step
- [ ] 11.2 Apply the harness to all five strategies as a parameterized test
- [ ] 11.3 Confirm the `LEAKY_BUCKET` parity case specifically exercises the burst-boundary
      scenario (the case that would catch a `tau` formula regression)

## 12. Redis as the authoritative clock

- [ ] 12.1 Write a test confirming evaluation decisions are computed from Redis's own clock and are
      unaffected by the calling test JVM's system time (e.g. no `now` argument is ever passed into
      a script)

## 13. Verification and wrap-up

- [ ] 13.1 Run `mvn verify` for `rls-domain`, `rls-application`, `rls-adapter-redis`; confirm every
      scenario in `specs/distributed-rate-limit-evaluation/spec.md` is covered by a passing test
- [ ] 13.2 Update `README.md` module list/build notes to reflect `rls-adapter-redis` now existing
      (Docker/Testcontainers requirement for running its tests)
- [ ] 13.3 Commit work on `feature/redis-adapter` following git-flow commit conventions (no AI
      co-authorship line)
