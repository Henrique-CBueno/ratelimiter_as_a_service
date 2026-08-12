## Context

`rls-bootstrap` currently serves one thing: the reactive REST API (`rls-adapter-rest`), authenticated
per-request by a custom `ApiTokenAuthenticationWebFilter` reading `Authorization: Bearer <token>`.
The whole application is built on Spring WebFlux (Netty, reactive Postgres/R2DBC, reactive Redis)
specifically to stay stateless and horizontally scalable — a core requirement from spec 1.

This change adds a second, human-facing surface: server-rendered Thymeleaf pages for onboarding,
login, and a resource-management dashboard. Unlike the REST API, a browser session is the natural
auth model for a dashboard (forms, redirects, flash messages) — but a naive session implementation
would reintroduce per-instance state, undermining the reason the app is reactive/stateless in the
first place.

## Goals / Non-Goals

**Goals:**
- Let a tenant register, log in, and manage their resources entirely through a browser.
- Keep every `rls-bootstrap` instance stateless, including the new session-based auth path, so
  horizontal scaling still works for the dashboard, not just the REST API.
- Reuse existing `rls-application` use cases directly (in-process calls), not by having the web
  layer call the REST API over HTTP — avoids a redundant network hop and duplicate DTOs.
- Keep the REST API's existing bearer-token auth on `/api/v1/**` completely unchanged.

**Non-Goals:**
- No "remember me" / long-lived tokens for the dashboard — session-only.
- No self-service password reset flow (out of scope for this change).
- No client-side framework/SPA — plain server-rendered Thymeleaf with standard form posts.
- No changes to `rls-domain`, `rls-adapter-redis`, `rls-adapter-persistence`, or the REST
  controllers themselves.

## Decisions

### 1. Reactive stack throughout: WebFlux + Thymeleaf's reactive view resolution + reactive Spring Security
The app only has Spring WebFlux on the classpath (Netty server), not the servlet stack. Spring Boot
auto-configures `ThymeleafReactiveViewResolver` when it detects a reactive web application, so
`spring-boot-starter-thymeleaf` works as-is. Spring Security must be configured via
`ServerHttpSecurity` / `SecurityWebFilterChain` (the reactive API), not the servlet-only
`HttpSecurity`/`WebSecurityConfigurerAdapter` API — mixing servlet-based Spring Security config into
a WebFlux app does not work, since Spring Boot's security auto-configuration branches based on
which web stack is present. `rls-adapter-web` and `rls-bootstrap` depend on
`spring-boot-starter-security`, which auto-detects the reactive stack already in use.

### 2. Session storage: Spring Session Data Redis (reactive)
Dashboard sessions are stored in the same Redis instance already used for rate-limit counters, via
`spring-session-data-redis`'s reactive session repository (auto-configured for WebFlux). This keeps
`rls-bootstrap` instances stateless: any instance can serve any request for a logged-in tenant,
preserving the horizontal-scalability requirement from spec 1. Alternative considered: the WebFlux
default in-memory session store — rejected because it pins a tenant's dashboard session to whichever
instance issued it, which is exactly the kind of per-instance state the rest of the architecture was
built to avoid.

### 3. Two independent auth mechanisms, scoped by path prefix
`/api/v1/**` keeps its existing `ApiTokenAuthenticationWebFilter` (a plain `WebFilter`, unrelated to
Spring Security) unchanged. `/app/**` (the new web UI) gets a `SecurityWebFilterChain` with
form-login, session auth, and CSRF protection. A second, permissive `SecurityWebFilterChain`
(higher `@Order` priority number = lower precedence, or matched narrowly to `/api/v1/**`) disables
CSRF and Spring Security's own auth handling for `/api/v1/**`, leaving that path exactly as it
behaves today — the existing `ApiTokenAuthenticationWebFilter` still runs for it, since it is
registered as an ordinary bean, independent of the security filter chain. Registration (`/app/register`)
and login (`/app/login`) are `permitAll` within the `/app/**` chain; everything else under `/app/**`
requires an authenticated session.

### 4. A new `LoginTenantUseCase`, separate from the REST API's token-based `AuthenticateTenantUseCase`
The REST API authenticates by a caller-supplied bearer token (`AuthenticateTenantUseCase.authenticate(rawToken)`).
A human logging into the dashboard has an email and password instead. `LoginTenantUseCase` looks the
tenant up by email (`TenantRepositoryPort.findByEmail`, already used by `RegisterTenantUseCase` for
duplicate-email checks) and verifies the password via the existing `SecretHasherPort.matches(...)`
(BCrypt, already used for tenant passwords per the `tenant-persistence` capability). No new ports or
persistence changes are needed — this composes existing pieces.

### 5. Web-specific error handling, not the REST API's `ProblemDetail`
`RestExceptionHandler` maps domain exceptions (`DuplicateEmailException`, etc.) to JSON
`ProblemDetail` bodies — wrong shape for a browser. The web controllers catch the same domain
exceptions and re-render the originating form with a human-readable inline error message (e.g.
"That email is already registered") instead of a JSON error response, following the standard
post/redirect/get pattern: successful form submissions redirect (so refreshing the result page
never re-submits), failed ones re-render the form.

### 6. API token is shown once, exactly like the REST API
The registration page and the "rotate token" action both display the raw token exactly once
(immediately after the response that created it), consistent with the REST API's behavior and the
`tenant-persistence` requirement that raw tokens are never stored or retrievable later.

## Risks / Trade-offs

- **[Risk]** A second `SecurityWebFilterChain` for `/api/v1/**` could accidentally start enforcing
  CSRF or auth on API requests, breaking the existing REST clients. → **Mitigation**: an explicit
  e2e test (analogous to the `rest-api` change's `HappyPathE2EIT`) that exercises a full REST flow
  (register → create resource → check) with both security chains active, asserting behavior is
  unchanged from before this change.
- **[Risk]** Introducing `spring-session-data-redis` adds a new serialization format for session
  data in Redis, sharing the same Redis instance as rate-limit counters. → **Mitigation**: Spring
  Session uses its own key namespace (`spring:session:*`) by default, distinct from the rate-limit
  keys' `rl:*` namespace, so there's no collision risk.
- **[Risk]** Reusing `rls-application` use cases directly from `rls-adapter-web` (rather than via
  HTTP) means two inbound adapters both depend on the same use case classes — a change to a use
  case's signature now has two call sites to update instead of one. → **Mitigation**: this is the
  same fan-in the REST adapter already has with `rls-application`; hexagonal architecture expects
  multiple inbound adapters sharing one application layer, so this is accepted as normal, not a
  defect to design around.

## Migration Plan

Purely additive: a new module, a new use case, and new bootstrap wiring. No existing endpoints,
tables, or Redis keys change shape. Deploys like any other spec in this project — no data migration,
no rollback complexity beyond reverting the merge.
