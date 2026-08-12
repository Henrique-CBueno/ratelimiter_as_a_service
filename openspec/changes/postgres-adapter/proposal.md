## Why

The domain core (spec 1) defines the `Tenant` and `RateLimitResource` aggregates, but nothing yet
persists them — every registered tenant and every configured resource would vanish on restart, and
nothing beyond a single in-memory instance could see them at all. Before the REST API (spec 4) can
let a tenant register, issue an API token, and configure resources, there must be a durable,
queryable store for that data. This change adds the PostgreSQL/R2DBC-backed persistence adapter:
repository implementations for both aggregates, the database schema (via Flyway migrations), and
the credential-hashing support that lets the domain accept only pre-hashed passwords/tokens
(per spec 1's design) while an adapter-level concern actually computes those hashes.

## What Changes

- Add `TenantRepositoryPort` and `ResourceRepositoryPort` to `rls-application`: reactive outbound
  ports for persisting and querying `Tenant` and `RateLimitResource` aggregates, independent of any
  specific database technology.
- Add `SecretHasherPort` to `rls-application`: a single port for turning a raw secret (a tenant
  password or a newly generated API token) into the pre-hashed value the domain constructors
  require, and for verifying a raw secret against a stored hash — kept out of `rls-domain` per
  spec 1's design (domain never sees or produces plaintext). One port covers both password and
  token hashing since the operation is identical (hash + constant-time verify); a separate
  `TokenHasherPort` would just duplicate the same two methods.
- Add the `rls-adapter-persistence` Maven module (Spring Data R2DBC + the PostgreSQL R2DBC driver)
  implementing all four ports above.
- Add Flyway migrations creating the `tenants`, `api_tokens`, and `rate_limit_resources` tables
  (schema already specified in `docs/architecture-plan.md`), including the `UNIQUE(tenant_id,
  resource_key)` constraint that backs the `rate-limit-resource-configuration` capability's
  uniqueness requirement from spec 1.
- Implement password/token hashing via BCrypt (`spring-security-crypto`, not the full Spring
  Security framework) so `rls-adapter-persistence` stays a lean dependency.
- Add Testcontainers-backed integration tests (real PostgreSQL) covering CRUD, uniqueness
  constraint enforcement, and migration correctness.
- No REST API or circuit breaker yet — this adapter, like `rls-adapter-redis` before it, is only
  exercised by its own test suite until spec 4 wires it in.

## Capabilities

### New Capabilities
- `tenant-persistence`: durable storage and retrieval of `Tenant` aggregates and their `ApiToken`s
  (registration, lookup by id/email/token hash, credential hashing and verification).
- `resource-persistence`: durable storage and retrieval of `RateLimitResource` aggregates scoped to
  a tenant (creation, lookup, listing, uniqueness enforcement, updates).

### Modified Capabilities
(none — `tenant-management` and `rate-limit-resource-configuration` from spec 1 keep the same
requirements; this change makes them durable, it does not change what they require)

## Impact

- **New dependency**: PostgreSQL, required at runtime from this spec onward via R2DBC; Testcontainers'
  PostgreSQL module required for the new module's test suite (already available via the
  `testcontainers-bom` import added to the parent `pom.xml` in spec 1).
- **New Maven module**: `rls-adapter-persistence`, depending on `rls-application` and `rls-domain`.
- **`rls-application`** gains its second and third ports (`TenantRepositoryPort`,
  `ResourceRepositoryPort`) plus the hashing ports, still with no database-specific dependency.
- No changes to `rls-adapter-redis` or the REST/web layers — this spec is independent of spec 2
  aside from both eventually being composed together in spec 4's `rls-bootstrap`.
