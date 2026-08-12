## 1. Setup

- [x] 1.1 Create `feature/rest-api` branch from `develop`
- [x] 1.2 Add `rls-adapter-resilience`, `rls-adapter-rest`, and `rls-bootstrap` modules to the
      parent `pom.xml` — also added a `resilience4j-bom` import (2.4.0) to the parent's
      `dependencyManagement`, and `springdoc-openapi.version` (2.9.0, the Spring Boot 3.x-compatible
      line — 3.x targets a newer Spring generation)
- [x] 1.3 `rls-adapter-resilience`: add `resilience4j-circuitbreaker` and `resilience4j-reactor`
      dependencies, depending on `rls-application`
- [x] 1.4 `rls-adapter-rest`: add `spring-boot-starter-webflux` and
      `springdoc-openapi-starter-webflux-ui`, depending on `rls-application` and `rls-domain`
- [x] 1.5 `rls-bootstrap`: add `spring-boot-starter-webflux` and the `spring-boot-maven-plugin`,
      depending on `rls-domain`, `rls-application`, and every adapter module built so far
      (`rls-adapter-redis`, `rls-adapter-persistence`, `rls-adapter-resilience`,
      `rls-adapter-rest`)
- [x] 1.6 Verify all three new modules compile empty (`mvn -pl rls-adapter-resilience,rls-adapter-rest,rls-bootstrap -am compile`)

## 2. Application use cases: tenant

- [x] 2.1 Write failing tests (fake `TenantRepositoryPort`/`SecretHasherPort`) for
      `RegisterTenantUseCase`: hashes the password, generates and fingerprints a new API token,
      saves the tenant, returns the raw token exactly once; a duplicate-email failure from the
      port propagates unchanged — while writing `AuthenticateTenantUseCase`'s design it became
      clear `SecretHasherPort` (BCrypt, salted) cannot back `findByActiveTokenHash` (needs a
      deterministic lookup value); added `ApiTokenGenerator.fingerprint()` (SHA-256, no port
      needed) for tokens specifically, keeping `SecretHasherPort` for passwords only — see the
      `## MODIFIED Requirements` added to `specs/tenant-persistence/spec.md` in this change
- [x] 2.2 Implement `RegisterTenantUseCase`
- [x] 2.3 Write failing tests for `AuthenticateTenantUseCase`: resolves the tenant for a valid
      active token hash; returns empty for an unknown or revoked token hash
- [x] 2.4 Implement `AuthenticateTenantUseCase`
- [x] 2.5 Write failing tests for `RotateApiTokenUseCase`: revokes the previous active token,
      issues and persists a new one, returns the new raw token exactly once
- [x] 2.6 Implement `RotateApiTokenUseCase` — confirmed `TenantRepositoryAdapter` (spec 3) needed
      no changes: it only ever stores/queries whatever hash string it's given, agnostic to how it
      was computed

## 3. Application use cases: resource

- [x] 3.1 Write failing tests and implement `CreateResourceUseCase` (persists a new resource under
      the given tenant; a duplicate-key failure from the port propagates unchanged)
- [x] 3.2 Write failing tests and implement `ListResourcesUseCase` (returns only the given
      tenant's resources)
- [x] 3.3 Write failing tests and implement `GetResourceUseCase` (returns the resource only when
      it belongs to the given tenant; empty otherwise, including when it belongs to a different
      tenant)
- [x] 3.4 Write failing tests and implement `UpdateResourceUseCase` (reconfigures strategy/quota
      only when the resource belongs to the given tenant; empty otherwise)
- [x] 3.5 Write failing tests and implement `DeleteResourceUseCase` (disables the resource only
      when it belongs to the given tenant; empty/false otherwise)

## 4. Application use case: check rate limit

- [x] 4.1 Write failing tests for `CheckRateLimitUseCase`'s happy path: resolves the resource by
      tenant+key via `ResourceRepositoryPort`, builds the `RateLimitKey`, delegates to
      `RateLimitEvaluationPort`, returns the resulting decision; returns empty when the resource
      is not found for that tenant — the use case takes the already-authenticated `Tenant` object
      directly (not just a `TenantId`), avoiding a redundant reload since the REST auth filter
      (group 7) will have already resolved it
- [x] 4.2 Implement the happy path of `CheckRateLimitUseCase`
- [x] 4.3 Write failing tests for the fallback path: when the evaluation port raises
      `RateLimitEvaluationUnavailableException`, the use case resolves the resource's effective
      fallback policy (`RateLimitResource.resolveFallbackPolicy`, spec 1) against the loaded
      tenant and returns a degraded decision matching that policy — also defined
      `RateLimitEvaluationUnavailableException` here (pulled forward from 5.1, same rationale as
      earlier specs) since the test needed it to exist
- [x] 4.4 Implement the fallback path in `CheckRateLimitUseCase` — fallback decisions use
      `quota.limit()` as both limit and (for fail-open) remaining, and `quota.window()` as the
      fail-closed retry-after, since the real remaining/reset values are unknowable while the
      evaluation dependency is down; both are marked `degraded = true`

## 5. Circuit breaker adapter (rls-adapter-resilience)

- [x] 5.1 Define `RateLimitEvaluationUnavailableException` in `rls-application` — already done at
      4.3 (pulled forward); confirmed present
- [x] 5.2 Implement `ResilientRateLimitEvaluationAdapter` in `rls-adapter-resilience`: wraps a
      delegate `RateLimitEvaluationPort`, applies a constructor-injected `CircuitBreaker` via
      `CircuitBreakerOperator`, maps circuit-open/any evaluation failure to
      `RateLimitEvaluationUnavailableException`
- [x] 5.3 Write a test using a real `CircuitBreaker` (low failure/volume thresholds) and a fake
      delegate that always fails: confirm repeated failures open the circuit, and once open,
      further calls fail fast with `RateLimitEvaluationUnavailableException` without invoking the
      delegate again — the fake delegate initially incremented its call counter eagerly (outside
      `Mono.defer`), which doesn't reflect how a real reactive adapter behaves (I/O only at
      subscription time) and made the circuit breaker's gating look like it wasn't working; fixed
      by deferring the side effect to subscription time, matching real adapter semantics

## 6. REST: tenant onboarding endpoints

- [x] 6.1 Define request/response DTOs for tenant registration and token rotation
- [x] 6.2 Implement `TenantController`: `POST /api/v1/tenants` (public), `POST
      /api/v1/tokens/rotate` (authenticated) — also added the shared `AuthenticatedTenant`
      exchange-attribute constant (group 7 will populate it) and a minimal `RestExceptionHandler`
      mapping `DuplicateEmailException`/`DuplicateResourceKeyException` → 409 (pulled forward from
      group 10, extended incrementally as later groups need more mappings)
- [x] 6.3 Write `WebTestClient` slice tests (use cases mocked): successful registration (`201`),
      duplicate email (`409`), successful rotation (`200`). The unauthenticated-rotation-`401`
      case moved to group 7's own tests: rejecting unauthenticated requests is entirely the auth
      `WebFilter`'s job, which this controller-only slice doesn't load, so there's nothing for the
      controller itself to assert about it. Hit and fixed a real `@WebFluxTest` pitfall along the
      way: a bare `@SpringBootConfiguration` test stand-in doesn't imply `@ComponentScan`, so the
      slice's controller filter had nothing to scan and silently registered zero controllers
      (all requests 404'd, not just the auth-dependent one) — fixed by using
      `@SpringBootApplication` for the test-scope stand-in instead; also switched the deprecated
      `@MockBean` to `@MockitoBean`

## 7. REST: authentication

- [x] 7.1 Implement `ApiTokenAuthenticationWebFilter`: extracts `Authorization: Bearer`, resolves
      the tenant via `AuthenticateTenantUseCase`, attaches the resolved `Tenant` to the exchange
      for downstream handlers, or short-circuits with `401`; allowlists `POST /api/v1/tenants`
      (also allowlisted `/v3/api-docs`, `/swagger-ui`, `/webjars` prefixes up front, anticipating
      group 13's OpenAPI docs needing to stay publicly reachable)
- [x] 7.2 Write slice tests: missing/invalid token rejected with `401` on a protected endpoint;
      valid token passes through and the downstream handler can read the resolved tenant;
      registration remains reachable with no token at all — tested the filter directly (Mockito
      fake use case + `MockServerWebExchange`/`MockServerHttpRequest`), not via `@WebFluxTest`,
      since the filter has no controller of its own to slice-test through; also added
      `reactor-test` to `rls-adapter-rest` (missing until now — `spring-boot-starter-test` doesn't
      pull it in automatically even for WebFlux projects)

## 8. REST: resource management endpoints

- [x] 8.1 Define request/response DTOs for resource creation, update, and reads — typed
      `strategyType`/`fallbackPolicy` fields as the actual domain enums rather than raw strings,
      so Jackson rejects invalid values at deserialization time for free
- [x] 8.2 Implement `ResourceController`: `POST`, `GET` (list), `GET /{id}`, `PUT /{id}`,
      `DELETE /{id}`, resolving the authenticated tenant from the exchange attribute set by the
      auth filter — added `ResourceNotFoundException` (adapter-layer only, not
      application/domain) mapped to 404 in `RestExceptionHandler`; since the use cases (group 3)
      already return empty for both "doesn't exist" and "belongs to another tenant", the
      controller treats both identically for free, satisfying design decision 6 without extra code
- [x] 8.3 Write `WebTestClient` slice tests: create (`201`), list scoped to tenant, get own
      (`200`) vs. another tenant's (`404`), update own (`200`) vs. another tenant's (`404`),
      delete own (`204`, soft-deleted) vs. another tenant's (`404`), duplicate key (`409`). As in
      group 6, the unauthenticated-`401` case is the auth filter's own test's responsibility, not
      each controller slice's. Extracted the fake-authenticated-tenant filter used by both
      controller slices into a shared `FakeAuthenticatedTenantFilterConfig`. Also hit and fixed a
      second real pitfall: `@PathVariable UUID id` failed at request time ("parameter name
      information not available via reflection") because the Maven compiler wasn't passing
      `-parameters`; added `<parameters>true</parameters>` to the parent POM's compiler plugin
      config globally, not just here, since every future `@PathVariable`/`@RequestParam` would
      hit the same issue

## 9. REST: rate-limit check endpoint

- [x] 9.1 Define request/response DTOs for the check endpoint — discovered `RateLimitDecision`
      (spec 1) has no `strategyType` field, but the response needs one; rather than duplicating a
      resource lookup in the REST layer, changed `CheckRateLimitUseCase.check(...)` (group 4) to
      return a `CheckResult(RateLimitDecision, StrategyType)` wrapper instead of the bare decision
      (updated `CheckRateLimitUseCaseTest` accordingly — same tests, new return shape)
- [x] 9.2 Implement `RateLimitCheckController`: `POST /api/v1/ratelimit/check`, mapping the
      resulting decision to `200`/`429` plus `RateLimit-Limit`/`RateLimit-Remaining`/
      `RateLimit-Reset`/`Retry-After` headers — generalized `ResourceNotFoundException` (group 8)
      with a `forKey(String)` factory alongside `forId(UUID)`, since this endpoint identifies the
      resource by key, not id
- [x] 9.3 Write `WebTestClient` slice tests: allowed (`200` + headers), denied (`429` +
      `Retry-After`), unconfigured resource (`404`). Unauthenticated-`401` remains the auth
      filter's own test's responsibility (groups 6/8's rationale applies here too)

## 10. REST: error handling

- [ ] 10.1 Implement a `RestExceptionHandler` mapping `DuplicateEmailException` /
      `DuplicateResourceKeyException` → `409`, not-found cases → `404`, authentication failures →
      `401`, each as an RFC 7807 `ProblemDetail` body
- [ ] 10.2 Write tests confirming each mapped exception produces the correct status and a
      well-formed `ProblemDetail` body

## 11. rls-bootstrap: composition root

- [ ] 11.1 Create the `@SpringBootApplication` main class and `application.yml` (Redis/PostgreSQL
      connection properties, server port)
- [ ] 11.2 Wire every bean by hand in `@Configuration` classes: `ReactiveRedisTemplate`
      (spec 2), R2DBC `DatabaseClient`/`ConnectionFactory` (spec 3), the shared `CircuitBreaker`
      + `ResilientRateLimitEvaluationAdapter` (this spec), `TenantRepositoryAdapter`,
      `ResourceRepositoryAdapter`, `BCryptSecretHasherAdapter`, all use cases from groups 2–4, the
      auth filter, and the three controllers
- [ ] 11.3 Write a `@SpringBootTest` smoke test (Testcontainers Redis + PostgreSQL) confirming the
      application context loads successfully with every bean wired

## 12. End-to-end tests

- [ ] 12.1 Write a full happy-path e2e test against the running `@SpringBootTest` context
      (`WebTestClient`, Testcontainers Redis + PostgreSQL): register a tenant, create a resource,
      call check repeatedly until the configured limit is exceeded, confirm `429` and headers on
      the request that exceeds it
- [ ] 12.2 Write a circuit-open fallback e2e test: force the shared `CircuitBreaker` bean into the
      open state directly (`transitionToOpenState()`, avoiding flaky container-pausing tricks),
      call check for a resource with a known fallback policy, confirm the response matches that
      policy with `degraded = true`, then transition the circuit back to closed

## 13. OpenAPI

- [ ] 13.1 Verify `/v3/api-docs` and the Swagger UI are reachable in the `@SpringBootTest` smoke
      test, confirming `springdoc-openapi` picked up the REST controllers

## 14. Verification and wrap-up

- [ ] 14.1 Run `mvn verify` across every module; confirm every scenario in
      `specs/tenant-onboarding-api/spec.md`, `specs/resource-management-api/spec.md`,
      `specs/rate-limit-check-endpoint/spec.md`, and `specs/resilient-evaluation-fallback/spec.md`
      is covered by a passing test
- [ ] 14.2 Update `README.md`: new modules, how to actually run `rls-bootstrap` locally (requires
      Redis + PostgreSQL, not just Testcontainers-for-tests), and a note that Docker Compose for
      one-command local startup is spec 6's scope
- [ ] 14.3 Commit work on `feature/rest-api` following git-flow commit conventions (no AI
      co-authorship line)
