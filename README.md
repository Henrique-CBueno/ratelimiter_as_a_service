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
- `rls-application` — use cases and ports
- `rls-adapter-redis` — distributed counters via Redis + Lua
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

Build instructions will be added once the Maven module skeleton exists (see `openspec/changes/setup-domain-core/tasks.md`).
