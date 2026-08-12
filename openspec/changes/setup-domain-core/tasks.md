## 1. Repository and git-flow setup

- [ ] 1.1 Run `git init`, create initial commit with `.gitignore` (Java/Maven/IDE) and a README describing the project
- [ ] 1.2 Create `develop` branch from `main`; confirm git-flow branch conventions (`feature/*`, `release/*`, `hotfix/*`) are documented in the README or CONTRIBUTING notes
- [ ] 1.3 Create `feature/setup-domain-core` branch from `develop` for this change's implementation work

## 2. Maven multi-module skeleton

- [ ] 2.1 Create parent `pom.xml` (`packaging=pom`) with `dependencyManagement` for Spring Boot BOM, Reactor BOM, JUnit5, AssertJ, Testcontainers BOM; fix Java version to 21
- [ ] 2.2 Create `rls-domain` module (no framework dependencies; JUnit5 + AssertJ as test-scope only)
- [ ] 2.3 Create `rls-application` module depending on `rls-domain` and `reactor-core`
- [ ] 2.4 Verify `mvn -pl rls-domain,rls-application -am compile` succeeds with the empty skeleton

## 3. Shared value objects (rls-domain)

- [ ] 3.1 Implement `TenantId` and `ResourceId` (UUID-backed identifier value objects)
- [ ] 3.2 Implement `ClientIp` with IPv4/IPv6 validation and normalization, with unit tests
- [ ] 3.3 Implement `FallbackPolicy` enum (`FAIL_OPEN`, `FAIL_CLOSED`)
- [ ] 3.4 Implement `Quota` (limit, window `Duration`, optional `burstCapacity`) with validation (`limit > 0`, `window > 0`), with unit tests
- [ ] 3.5 Implement `RateLimitKey` (tenantId + resourceId + clientIp + strategyCode)
- [ ] 3.6 Implement `RateLimitDecision` (allowed, limit, remaining, resetAt, retryAfter nullable, degraded flag)
- [ ] 3.7 Implement `RateLimitState` hierarchy: `FixedWindowState`, `SlidingLogState`, `SlidingCounterState`, `TokenBucketState`, `LeakyBucketState`

## 4. Tenant aggregate (rls-domain) — capability: tenant-management

- [ ] 4.1 Write failing tests for tenant registration (valid case, invalid email) per `specs/tenant-management/spec.md`
- [ ] 4.2 Implement `Tenant` aggregate (name, email, passwordHash, status, defaultFallbackPolicy) to pass the tests
- [ ] 4.3 Write failing tests for status lifecycle (`suspend`/`reactivate`)
- [ ] 4.4 Implement `suspend()`/`reactivate()` behavior
- [ ] 4.5 Write failing tests for `ApiToken` issuance and rotation (including "no active token" case)
- [ ] 4.6 Implement `ApiToken` entity and `issueApiToken()`/`rotateApiToken()` on `Tenant`
- [ ] 4.7 Write failing test asserting no method exposes a raw/plaintext credential
- [ ] 4.8 Confirm all `tenant-management` scenarios from the spec are covered by passing tests

## 5. RateLimitResource aggregate (rls-domain) — capability: rate-limit-resource-configuration

- [ ] 5.1 Write failing tests for resource creation (valid case, non-positive quota rejected) per `specs/rate-limit-resource-configuration/spec.md`
- [ ] 5.2 Implement `RateLimitResource` aggregate (tenantId, resourceKey, strategyType, quota, fallbackPolicy nullable, enabled) to pass the tests
- [ ] 5.3 Write failing tests for fallback policy inheritance (unset vs explicit)
- [ ] 5.4 Implement fallback policy resolution helper (`resolveFallbackPolicy(tenant)` or equivalent)
- [ ] 5.5 Write failing tests for enable/disable (soft delete)
- [ ] 5.6 Implement `enable()`/`disable()`
- [ ] 5.7 Write failing tests for `reconfigure()` (valid change, invalid quota rejected without mutating state)
- [ ] 5.8 Implement `reconfigure(newStrategyType, newQuota)`
- [ ] 5.9 Confirm all `rate-limit-resource-configuration` scenarios from the spec are covered by passing tests

## 6. Rate limit strategies (rls-domain) — capability: rate-limit-strategy-evaluation

- [ ] 6.1 Define `RateLimitStrategy` interface (`evaluate(state, quota, now) -> RateLimitDecision` + new state, pure/synchronous)
- [ ] 6.2 Define `StrategyType` enum (`FIXED_WINDOW`, `SLIDING_WINDOW_LOG`, `SLIDING_WINDOW_COUNTER`, `TOKEN_BUCKET`, `LEAKY_BUCKET`)
- [ ] 6.3 Write failing tests for `FixedWindowStrategy` (allow under limit, deny at limit, window rollover)
- [ ] 6.4 Implement `FixedWindowStrategy`
- [ ] 6.5 Write failing tests for `SlidingWindowLogStrategy` (allow, deny, expired timestamp purge)
- [ ] 6.6 Implement `SlidingWindowLogStrategy`
- [ ] 6.7 Write failing tests for `SlidingWindowCounterStrategy` (allow under weighted estimate, deny at estimate)
- [ ] 6.8 Implement `SlidingWindowCounterStrategy`
- [ ] 6.9 Write failing tests for `TokenBucketStrategy` (allow with tokens available, deny when empty, refill proportional to elapsed time)
- [ ] 6.10 Implement `TokenBucketStrategy`
- [ ] 6.11 Write failing tests for `LeakyBucketStrategy` (GCRA allow/deny at boundary, TAT persistence only on allow)
- [ ] 6.12 Implement `LeakyBucketStrategy`
- [ ] 6.13 Write failing tests asserting every strategy's denied decision includes a non-null `retryAfter`
- [ ] 6.14 Add property-based or table-driven edge-case tests (remaining never negative, allowed never exceeds limit within a window) for all five strategies
- [ ] 6.15 Write failing tests for `StrategyRegistry` (resolves known types, rejects unknown types)
- [ ] 6.16 Implement `StrategyRegistry`
- [ ] 6.17 Confirm all `rate-limit-strategy-evaluation` scenarios from the spec are covered by passing tests

## 7. Verification and wrap-up

- [ ] 7.1 Run `mvn verify` for `rls-domain` and `rls-application`; confirm 100% of the specs' scenarios map to passing tests
- [ ] 7.2 Review branch coverage of the five strategies; add missing edge-case tests if any branch is uncovered
- [ ] 7.3 Update README with build/test instructions (`mvn verify`) and a short description of the module layout
- [ ] 7.4 Commit work on `feature/setup-domain-core` following git-flow commit conventions (no AI co-authorship line)
