## 1. Setup

- [x] 1.1 Create `feature/postgres-adapter` branch from `develop`
- [x] 1.2 Add `rls-adapter-persistence` module to the parent `pom.xml`; add the R2DBC PostgreSQL
      driver (main), Flyway core + the PostgreSQL JDBC driver (main, migration-time only),
      `spring-security-crypto` (main), depending on `rls-application` and `rls-domain`; add the
      Testcontainers PostgreSQL module (test scope) — the R2DBC PostgreSQL driver now lives under
      `org.postgresql:r2dbc-postgresql` (moved from the old `io.r2dbc:r2dbc-postgresql`
      coordinates); also added `flyway-database-postgresql`, split out of `flyway-core` in
      modern Flyway versions
- [x] 1.3 Verify `mvn -pl rls-adapter-persistence -am compile` succeeds with the empty module
      skeleton

## 2. Database schema (Flyway)

- [x] 2.1 Write `V1__init.sql` creating `tenants`, `api_tokens`, and `rate_limit_resources`,
      matching the schema in `docs/architecture-plan.md`, including `UNIQUE(email)` on `tenants`,
      `UNIQUE(token_hash)` on `api_tokens`, and `UNIQUE(tenant_id, resource_key)` on
      `rate_limit_resources`
- [x] 2.2 Write a Testcontainers test that runs the migration against a real PostgreSQL instance
      and asserts it succeeds (tables exist with the expected constraints) — also introduced
      `AbstractPostgresIT` (singleton-container pattern, same as `AbstractRedisIT` in
      rls-adapter-redis), used by every subsequent PostgreSQL integration test in this module

## 3. Domain reconstruction support (rls-domain)

- [x] 3.1 Write failing tests for `Tenant.reconstitute(...)` reproducing an existing tenant's full
      state (id, name, email, password hash, status, default fallback policy, full token list
      including revoked ones) without going through `register()`'s new-aggregate invariants
- [x] 3.2 Implement `Tenant.reconstitute(...)` — added a small `ApiTokenData` record (id, hash,
      prefix, createdAt, revokedAt) as the adapter-facing shape for token history, and a
      package-private `ApiToken.restore(...)` (reachable only from within `domain.tenant`, i.e.
      from `Tenant.reconstitute`) since `ApiToken` itself doesn't need adapter-visible
      reconstruction — only the aggregate root does
- [x] 3.3 Write failing tests for `RateLimitResource.reconstitute(...)` reproducing an existing
      resource's full state without going through `create()`'s new-aggregate invariants
- [x] 3.4 Implement `RateLimitResource.reconstitute(...)`

## 4. Secret hashing (SecretHasherPort)

- [x] 4.1 Define `SecretHasherPort` in `rls-application` (`hash(raw): String`,
      `matches(raw, hash): boolean`)
- [x] 4.2 Write failing tests for the BCrypt-based implementation: a hash verifies against the
      secret it was created from, fails against a different secret, and is not equal to the raw
      secret itself — also asserted BCrypt's salting means hashing the same secret twice yields
      different hashes, both still verifying correctly
- [x] 4.3 Implement `BCryptSecretHasherAdapter` in `rls-adapter-persistence` using
      `spring-security-crypto`'s `BCryptPasswordEncoder`

## 5. Repository ports (rls-application)

- [x] 5.1 Define `TenantRepositoryPort` (`save`, `findById`, `findByEmail`,
      `findByActiveTokenHash`)
- [x] 5.2 Define `ResourceRepositoryPort` (`save`, `findById`, `findByTenantAndKey`,
      `findAllByTenant`)
- [x] 5.3 Define `DuplicateEmailException` and `DuplicateResourceKeyException` in `rls-application`
      for the ports to throw instead of leaking R2DBC/PostgreSQL-specific exceptions

## 6. Tenant repository adapter

- [x] 6.1 Implement row mapping between the `tenants`/`api_tokens` tables and `Tenant`/`ApiToken`
      (via `Tenant.reconstitute(...)`) — used `DatabaseClient` directly (not
      `ReactiveCrudRepository`/`R2dbcEntityTemplate`), per design decision 2, since `Tenant` spans
      two tables
- [x] 6.2 Implement `save()` (upsert the tenant row; insert any new token rows, update revocation
      status on existing ones) — both upserts use `INSERT ... ON CONFLICT (id) DO UPDATE`
- [x] 6.3 Implement `findById`, `findByEmail`, `findByActiveTokenHash`
- [x] 6.4 Catch the unique-email constraint violation on save and translate it into
      `DuplicateEmailException` — detected by walking the exception cause chain for an
      `R2dbcException` with SQL state `23505` (Postgres unique_violation), rather than relying on
      Spring's automatic R2DBC exception translation being active in this raw `DatabaseClient`
      setup (untested assumption, so avoided)

## 7. Resource repository adapter

- [ ] 7.1 Implement row mapping between `rate_limit_resources` and `RateLimitResource` (via
      `RateLimitResource.reconstitute(...)`)
- [ ] 7.2 Implement `save()`, `findById`, `findByTenantAndKey`, `findAllByTenant`
- [ ] 7.3 Catch the unique `(tenant_id, resource_key)` constraint violation on save and translate
      it into `DuplicateResourceKeyException`

## 8. Tenant persistence integration tests (Testcontainers PostgreSQL)

- [ ] 8.1 Round-trip test: register a tenant, save, load by id, assert identical observable state
- [ ] 8.2 `findByEmail`: found for an existing tenant, empty for a non-existent one
- [ ] 8.3 Duplicate email is rejected with `DuplicateEmailException`, original tenant unchanged
- [ ] 8.4 Issuing and rotating an API token, saving, and reloading reflects the same active/revoked
      state as before saving
- [ ] 8.5 `findByActiveTokenHash`: resolves the owning tenant for an active token hash, returns
      empty for a revoked/rotated-away token hash

## 9. Resource persistence integration tests (Testcontainers PostgreSQL)

- [ ] 9.1 Round-trip test: create a resource, save, load by id, assert identical observable state
- [ ] 9.2 `findByTenantAndKey`: found for an existing resource, empty for a non-existent one
- [ ] 9.3 `findAllByTenant` returns only that tenant's resources, not other tenants'
- [ ] 9.4 Duplicate `resourceKey` for the same tenant is rejected with `DuplicateResourceKeyException`;
      the same key across two different tenants is allowed
- [ ] 9.5 Reconfiguring the strategy/quota and disabling a resource are both persisted across a
      save-then-reload cycle

## 10. Verification and wrap-up

- [ ] 10.1 Run `mvn verify` for `rls-domain`, `rls-application`, `rls-adapter-persistence`;
      confirm every scenario in `specs/tenant-persistence/spec.md` and
      `specs/resource-persistence/spec.md` is covered by a passing test
- [ ] 10.2 Update `README.md` module list/build notes to reflect `rls-adapter-persistence` now
      existing (PostgreSQL/Testcontainers requirement for running its tests)
- [ ] 10.3 Commit work on `feature/postgres-adapter` following git-flow commit conventions (no AI
      co-authorship line)
