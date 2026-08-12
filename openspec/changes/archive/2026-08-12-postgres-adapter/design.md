## Context

Specs 1 and 2 delivered the domain core (`Tenant`, `RateLimitResource`, the five strategies) and the
Redis-backed rate-limit evaluation adapter, but nothing yet persists tenant registrations or
resource configurations — they exist only for the lifetime of a single test. This spec adds the
PostgreSQL/R2DBC persistence adapter so that data survives restarts and is visible to every
stateless instance, which is the same architectural requirement (horizontal scalability without
hidden per-instance state) that motivated spec 2's Redis adapter. No REST API, circuit breaker, or
Spring Boot application exists yet (specs 4+); this adapter is validated by its own test suite.

## Goals / Non-Goals

**Goals:**
- Implement `TenantRepositoryPort` and `ResourceRepositoryPort` (new outbound ports in
  `rls-application`) against PostgreSQL via R2DBC (fully reactive, no JDBC thread-per-call model).
- Implement `SecretHasherPort` using BCrypt, keeping the domain's "accept only pre-hashed values"
  invariant from spec 1 intact — hashing is computed by the adapter, not the domain.
- Define the schema via versioned Flyway migrations, matching the model in
  `docs/architecture-plan.md`: `tenants`, `api_tokens`, `rate_limit_resources`.
- Enforce the `rate-limit-resource-configuration` capability's uniqueness requirement
  (`resourceKey` unique per tenant, spec 1) at the database level via a `UNIQUE(tenant_id,
  resource_key)` constraint, translated into a domain-meaningful exception rather than a leaked
  R2DBC/driver exception.

**Non-Goals:**
- No REST/HTTP surface, no circuit breaker around database calls (spec 4's
  `rls-adapter-resilience`), no Spring Boot application assembly (spec 4's `rls-bootstrap`).
- No connection pooling/tuning decisions beyond R2DBC's defaults — production-grade pool sizing is
  deferred to `rls-bootstrap`'s configuration (spec 4+), where actual deployment topology is known.
- No caching of tenant/resource lookups — the REST layer's hot-path caching concern noted in spec
  1's design (Postgres lookup on every `check` call) remains an explicitly deferred optimization,
  unaffected by this spec.
- No changes to `rls-domain` or `rls-adapter-redis` — this spec only adds new ports/adapters.

## Decisions

**1. R2DBC (Spring Data R2DBC), not JPA/Hibernate with a blocking JDBC driver wrapped in
`Mono.fromCallable`.**
The whole application is required to be reactive end-to-end; wrapping blocking JDBC calls would
require a dedicated bounded elastic scheduler and reintroduce thread-per-call blocking behavior
under the hood, undermining the reactive model the rest of the stack (Reactor in
`rls-application`, Redis Reactive in `rls-adapter-redis`) already commits to. R2DBC's PostgreSQL
driver gives genuinely non-blocking database I/O consistent with that choice.

**2. Repositories are hand-written adapter classes implementing the application ports directly,
not Spring Data R2DBC's repository-interface-proxy mechanism.**
Spring Data R2DBC's `ReactiveCrudRepository` auto-implements interfaces bound to a single entity
type with framework-generated queries. `Tenant` is an aggregate with a child collection
(`ApiToken`s) that doesn't map to one table row, so persisting/reconstituting it needs explicit
mapping code either way. Writing `TenantRepositoryAdapter`/`ResourceRepositoryAdapter` classes
(using `R2dbcEntityTemplate` or `DatabaseClient` directly) makes that mapping explicit and keeps
`rls-domain`'s aggregates free of persistence annotations, preserving the hexagonal boundary
established in spec 1 (adapters translate; domain stays ignorant of the adapter's technology).

**3. Domain reconstruction uses a distinctly-named public `reconstitute(...)` factory, separate
from the `register`/`create` factories used for new aggregates.**
Spec 1's `Tenant.register(...)` and `RateLimitResource.create(...)` factories generate a new ID and
enforce "this is a brand-new aggregate" invariants (e.g., `ACTIVE` status, empty token list). Loading
an existing row from the database needs to reconstruct an aggregate with its *existing* ID, status,
and history — a different concern from creation. Since `rls-adapter-persistence` is a separate
Maven module/package from `rls-domain`, Java package-private visibility cannot restrict this to
"adapters only" the way it could within a single package; instead this spec adds a clearly-named
public factory (`Tenant.reconstitute(...)`, `RateLimitResource.reconstitute(...)`) whose name and
Javadoc make its "repository use only, do not call to create a new aggregate" intent explicit,
following the common DDD convention for this exact cross-package situation. `register`/`create`
keep their spec 1 invariants (new ID, brand-new-aggregate defaults) unchanged.

**4. `SecretHasherPort` lives in `rls-adapter-persistence` via `spring-security-crypto`
(`BCryptPasswordEncoder`), not the full Spring Security framework.**
`spring-security-crypto` is a small, standalone artifact with no servlet/webflux security filter
chain dependencies — appropriate since no authentication framework exists yet (that's a REST-layer
concern for spec 4). This satisfies spec 1's design note that hashing is an adapter/infrastructure
concern the domain never sees.

**5. Uniqueness violations surface as a domain-meaningful exception, not a raw R2DBC exception.**
`ResourceRepositoryAdapter` catches the database's unique-constraint violation (on insert/update of
`rate_limit_resources`) and translates it into a `DuplicateResourceKeyException` (or similar,
defined in `rls-application` alongside the port), so callers in future specs (the REST layer) can
handle "resource key already exists for this tenant" without depending on R2DBC/PostgreSQL-specific
exception types — keeping the port's contract technology-agnostic, consistent with hexagonal
boundaries.

**6. Flyway for migrations, run against the same PostgreSQL instance the R2DBC pool targets.**
Flyway itself uses a blocking JDBC connection (it has no reactive variant), which is the accepted,
universal pattern even in reactive Spring applications — migrations run once at startup/test setup,
not on the request hot path, so a short blocking JDBC connection for that specific purpose does not
compromise the reactive runtime model.

## Risks / Trade-offs

- [Risk] R2DBC's ecosystem and tooling are less mature than JDBC/JPA's (e.g., no first-class
  lazy-loading, more manual mapping) → Mitigation: accepted deliberately for Decision 1; the
  explicit hand-written mapping from Decision 2 is more code but fully predictable, with no hidden
  N+1 query surprises to debug later.
- [Risk] Hand-written reconstruction logic (Decision 3) could drift from the aggregates' invariants
  if spec 1's `Tenant`/`RateLimitResource` change later without updating the reconstruction path →
  Mitigation: reconstruction tests assert round-trip fidelity (save then load must reproduce an
  aggregate with identical observable state) for every field, catching drift immediately.
- [Risk] Flyway's blocking JDBC dependency adds a second database driver (`postgresql` JDBC, plus
  the R2DBC one) to the module → Mitigation: this is Flyway's standard, well-established mode of
  operation; the JDBC driver is only used at migration time, never on the reactive request path.
- [Trade-off] No connection-level integration with a circuit breaker yet (Non-Goal) means this
  adapter's tests don't exercise failure/timeout behavior — acceptable since `rls-adapter-resilience`
  (spec 4) is explicitly responsible for that decorator, and wrapping it prematurely here would
  duplicate work once the real decorator exists.

## Migration Plan

Not applicable in the schema-migration-tool sense beyond the Flyway scripts themselves (which are
the actual "migration" this spec produces); there is no existing production data to migrate, since
no earlier spec persisted anything.

## Open Questions

- None blocking this spec. Whether `ApiToken` rotation history is ever pruned (vs. kept forever) is
  a product/retention question deferred until it's actually relevant (no spec currently reads old,
  revoked tokens for anything beyond "is this the active one").
