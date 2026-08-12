# Rate Limiter as a Service

Rate Limiter as a Service (RLS) is a reactive Java application that lets tenants register their
own rate-limiting policies and check, per request, whether a given resource + client IP is
authorized under that policy.

Each tenant can configure, per resource, one of five strategies:

- Fixed Window
- Sliding Window Log
- Sliding Window Counter
- Token Bucket
- Leaky Bucket (GCRA)

with a custom limit and time window.

The service is designed to be stateless and horizontally scalable: all rate-limit counters live in
Redis (evaluated atomically via Lua scripts to avoid race conditions across concurrent instances),
tenant/resource configuration lives in PostgreSQL, and a circuit breaker protects the hot path
against Redis unavailability with a per-tenant configurable fallback policy (fail-open /
fail-closed). A Thymeleaf front end provides tenant onboarding, login, and a management dashboard.

## Architecture

The project follows hexagonal architecture (ports & adapters), Domain-Driven Design, and
Test-Driven Development. See `docs/architecture-plan.md` for the full architectural design and
`openspec/` for the change-by-change specifications driving the implementation.

Modules (added incrementally, one per spec):

- `rls-domain` — pure Java domain model (aggregates, value objects, the five rate-limit strategies)
- `rls-application` — use cases and ports (`RateLimitEvaluationPort`)
- `rls-adapter-redis` — distributed counters via Redis + Lua (one atomic `EVAL` script per
  strategy); integration tests use Testcontainers, so **Docker must be running** to execute them
- `rls-adapter-persistence` — tenant/resource persistence via PostgreSQL + R2DBC
- `rls-adapter-resilience` — circuit breaker decorator
- `rls-adapter-rest` — reactive REST API
- `rls-adapter-web` — Thymeleaf front end
- `rls-bootstrap` — Spring Boot application assembly

## Git workflow

This repository follows the git-flow branching model:

- `main` — always reflects production-ready code
- `develop` — integration branch for completed features
- `feature/<name>` — one branch per OpenSpec change, branched from `develop`, merged back into
  `develop` when the change's tasks are complete
- `release/<version>` — release stabilization branches, branched from `develop`, merged into both
  `main` and `develop`
- `hotfix/<name>` — urgent fixes branched from `main`, merged into both `main` and `develop`

## Building

Requires JDK 21+, Maven, and (for `rls-adapter-redis`'s integration tests) a running Docker
daemon — its tests start a real `redis:7-alpine` container via Testcontainers. From the repository
root:

```
mvn verify
```

This compiles every module and runs the full test suite: unit tests via Surefire (`*Test.java`,
no external dependencies) and integration tests via Failsafe (`*IT.java`, needs Docker). A JaCoCo
coverage report is generated per module at `target/site/jacoco/index.html`.

To build/test a single module (and the modules it depends on), use `-pl` with `-am`, e.g.:

```
mvn -pl rls-adapter-redis -am verify
```

Modules existing so far: `rls-domain`, `rls-application`, `rls-adapter-redis`. The remaining
modules listed above are added incrementally by later OpenSpec changes.
