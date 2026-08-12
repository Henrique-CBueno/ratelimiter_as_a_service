## Context

Spec 1 delivered the domain core: `Tenant`, `RateLimitResource`, and the five rate-limit strategies
as pure, synchronous, in-memory algorithms (`RateLimitStrategy.evaluate(state, quota, now)`),
deliberately free of I/O so they could be TDD'd in isolation and serve as an executable
specification of each algorithm's behavior. Those pure implementations are not what runs in
production: under real concurrency, with multiple stateless instances of the application handling
requests for the same tenant/resource/IP simultaneously, only an atomic operation against a shared
store can guarantee the configured limit is never exceeded. This spec builds that shared store
integration: a Redis-backed `rls-adapter-redis` module implementing a new `RateLimitEvaluationPort`
in `rls-application`, using Lua scripts (`EVAL`) so each strategy's full read-check-write sequence
executes as a single atomic operation inside Redis.

No REST API, persistence, or circuit breaker exists yet (specs 3–4). This adapter is validated
through its own test suite (Testcontainers-backed) and is not reachable through any HTTP endpoint
until spec 4 wires it in.

## Goals / Non-Goals

**Goals:**
- Implement `RateLimitEvaluationPort` (new outbound port in `rls-application`) against Redis, with
  one Lua script per strategy performing the algorithm atomically.
- Prove atomicity empirically: concurrency tests firing many parallel requests at the same key must
  never allow more than `limit` through.
- Keep the Redis implementation behaviorally identical to the pure domain strategies from spec 1
  via parity tests, so the domain module remains the single source of truth for "what each
  algorithm does" and the Lua scripts are a verified translation of it, not an independent
  reimplementation that could silently drift.
- Source "now" from Redis itself (`redis.call('TIME')`), never from the calling application
  instance, so clock skew between horizontally-scaled instances cannot affect rate-limit decisions.

**Non-Goals:**
- No REST/HTTP surface — this adapter is only exercised by its own tests in this spec.
- No circuit breaker around Redis calls — that is spec 4's `rls-adapter-resilience`, which will
  decorate this adapter's port implementation without this spec needing to anticipate it.
- No `docker-compose.yml` for manually running a local Redis — Testcontainers provides Redis for
  the test suite; a docker-compose file for interactive/manual use is spec 6's scope.
- No changes to the `rate-limit-strategy-evaluation` domain spec from spec 1 — this change treats
  it as a fixed, already-validated contract to replicate, not something to revise.

## Decisions

**1. One Lua script per strategy, each self-contained and idempotent-safe via `EVAL`.**
Redis guarantees a Lua script runs atomically with respect to all other commands — no other client
can interleave commands mid-script. This is what makes the read-check-write sequence of each
algorithm race-free across concurrent application instances, which is the core requirement driving
this entire change (stateless horizontal scalability without race conditions). Alternative
considered: `WATCH`/`MULTI`/`EXEC` optimistic transactions from the client. Rejected — it requires
retry loops on contention (extra round-trips, more complex adapter code, worse tail latency under
load) where a single `EVAL` gives the same guarantee in one round-trip.

**2. Redis is the clock (`redis.call('TIME')`), never the calling instance.**
The application is explicitly required to be stateless and horizontally scalable — many instances,
potentially on different hosts with imperfect clock synchronization, may evaluate requests against
the same key. If each instance supplied its own `now`, two instances with skewed clocks could
disagree about which window a request falls into, corrupting the shared counters. Redis, as the
single shared coordination point, is the only clock all instances can agree on by construction.
Alternative considered: pass `now` as a script argument (as the domain layer does). Rejected for
the adapter specifically — it would reintroduce the exact cross-instance consistency problem this
adapter exists to solve.

**3. Key naming embeds the strategy: `rl:{tenantId}:{resourceId}:{clientIp}:{strategyCode}`.**
Each strategy uses a different Redis data structure for its state (`STRING`, `HASH`, `ZSET`). If a
tenant changes a resource's strategy, reusing the same key would risk a `WRONGTYPE` error against
data left behind by the previous strategy. Embedding the strategy code in the key means a strategy
change simply starts writing to a new key; the old key is left to expire via its own TTL. Trade-off
accepted: a strategy change effectively resets rate-limit history for that key (acceptable — a
tenant changing their rate-limit strategy expecting a clean slate is reasonable behavior, and
preserving history across incompatible algorithms is not a stated requirement).

**4. `RateLimitEvaluationPort` lives in `rls-application`; only its Redis implementation lives in
`rls-adapter-redis`.**
Consistent with the hexagonal architecture established in spec 1: `rls-application` defines the
reactive contract (`Mono<RateLimitDecision> evaluate(...)`), and `rls-adapter-redis` is the only
module that knows about Lettuce/Spring Data Redis. This keeps the port swappable in principle (even
though no alternative implementation is planned) and keeps `rls-domain`/`rls-application` free of
any Redis-specific types.

**5. Parity tests reuse the domain module's strategies as the assertion oracle.**
Each Lua script's algorithm is a manual translation of its corresponding `RateLimitStrategy`
implementation from spec 1. Manual translations between languages (Java → Lua) are a well-known
source of silent behavioral drift. Rather than trusting the translation by inspection, parity tests
drive an identical, deterministic sequence of `(quota, now)` calls through both the Lua script
(against a real Testcontainers Redis) and the pure Java strategy, asserting identical `allowed`/
`remaining` outcomes at every step. This is only possible because spec 1 kept the domain strategies
as pure, deterministic functions — validating that architectural choice.
**Known translation detail to carry over exactly**: the Leaky Bucket domain implementation computes
burst tolerance as `tau = (burstCapacity - 1) * emissionInterval` (not `burstCapacity *
emissionInterval` — an off-by-one that spec 1's own invariant tests caught and fixed). The Lua
GCRA script must use the same formula, or the parity test for `LEAKY_BUCKET` will fail.

**6. Reactive Redis client: Spring Data Redis Reactive (Lettuce).**
Lettuce is the reactive Redis client Spring Data Redis wraps; it integrates with Project Reactor
(already the reactive contract used by `rls-application` since spec 1) without a thread-per-call
model, consistent with the "reactive Java application" requirement. `ReactiveRedisTemplate` (or the
lower-level `ReactiveScriptExecutor`) invokes `EVAL` and maps the Lua return value into the port's
`Mono<RateLimitDecision>`.

## Risks / Trade-offs

- [Risk] A Lua script bug could silently diverge from the domain algorithm despite passing simple
  tests → Mitigation: parity tests (Decision 5) run the *same* deterministic scenarios through both
  implementations rather than hand-picking separate expectations per side.
- [Risk] `EVAL` scripts are opaque strings with no compiler — a typo only surfaces at runtime →
  Mitigation: scripts are stored as versioned `.lua` resource files (not inline Java strings) so
  they can be reviewed and tested in isolation; every script has a dedicated unit-of-integration
  test beyond the shared parity suite.
- [Risk] Testcontainers requires Docker to be available in CI/dev environments; without it, this
  module's tests cannot run → Mitigation: this is an accepted, standard trade-off for integration
  tests against real infrastructure (explicitly chosen in spec 1's design over mocking Redis, which
  would not prove atomicity); documented in the module's test setup.
- [Trade-off] Embedding `clientIp` directly in the Redis key (Decision 3) means IPv6 addresses
  produce longer keys than IPv4 — acceptable; Redis has no practical key-length concern at this
  scale, and `ClientIp` (spec 1) already normalizes the textual form before it reaches the key.

## Migration Plan

Not applicable — this adapter is net-new and not yet wired into any reachable entry point (no REST
API exists until spec 4). Nothing in production or in a prior spec is being changed or migrated.

## Open Questions

- None blocking this spec. Whether the future circuit breaker (spec 4) wraps the whole
  `RateLimitEvaluationPort.evaluate(...)` call or something more granular is deferred to spec 4,
  where the resilience module's design will be decided with full context.
