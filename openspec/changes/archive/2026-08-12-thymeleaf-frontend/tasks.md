## 1. Setup

- [x] 1.1 Create the `rls-adapter-web` module (`pom.xml` depending on `rls-domain`, `rls-application`,
      `spring-boot-starter-webflux`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-security`,
      `spring-session-data-redis`, `spring-boot-starter-test`, `reactor-test`); add it to the parent
      `pom.xml`'s `<modules>`
- [x] 1.2 Verify the module compiles with an empty `src/main/java` package placeholder — compiles
      cleanly; Spring Boot's dependency management resolved `spring-session-data-redis`'s version
      with no explicit version needed

## 2. Application use case: login by email and password

- [x] 2.1 Write a failing test for `LoginTenantUseCase.login(email, password)` returning the
      matching `Tenant` when the email exists and the password matches (using the existing
      in-memory `TenantRepositoryPort` fake and `FakeSecretHasherPort` from `rls-application`'s test
      sources), then implement it to pass
- [x] 2.2 Write a failing test for login failing (empty `Mono`, not an exception) when the email has
      no matching tenant, then confirm it passes
- [x] 2.3 Write a failing test for login failing when the email matches but the password does not,
      then confirm it passes — implemented as `findByEmail(...).filter(matches(...))`, so both
      failure cases fall out of the same one-line implementation as an empty `Mono`

## 3. Thymeleaf templates

- [x] 3.1 Create a base layout fragment (nav, flash-message region) reused by every page —
      `fragments/layout.html` (`nav` and `error(message)` fragments)
- [x] 3.2 Create the registration page template and its post-submission "token revealed once" page
- [x] 3.3 Create the login page template
- [x] 3.4 Create the resource list (dashboard) template, the create/edit resource form template, and
      a not-found template for cross-tenant resource access
- [x] 3.5 Create the settings/token page template, including its "token revealed once" state after
      rotation

## 4. Web controllers

- [x] 4.1 Implement the registration controller (`GET`/`POST /app/register`), calling
      `RegisterTenantUseCase`, rendering the duplicate-email case as an inline form error per
      `web-tenant-onboarding`
- [x] 4.2 Implement the resource dashboard controller (`GET /app/resources`, create, edit, delete),
      calling the same use cases the REST API uses (`ListResourcesUseCase`, `CreateResourceUseCase`,
      `UpdateResourceUseCase`, `DeleteResourceUseCase`), scoped to the authenticated tenant from the
      session — the session's `TenantPrincipal` carries the tenant id directly, so no repository
      lookup is needed per request to scope these calls
- [x] 4.3 Implement the settings/token controller (view token prefix, rotate), calling
      `RotateApiTokenUseCase` — added a small `GetTenantUseCase` (mirrors `GetResourceUseCase`,
      wraps the existing `TenantRepositoryPort.findById`) since the settings page needs the full
      `Tenant` to read its active token prefix and to pass to `RotateApiTokenUseCase.rotate(Tenant)`
- [x] 4.4 Implement cross-tenant resource access as a not-found response (edit/delete for another
      tenant's resource id), matching the REST API's existing `resource-management-api` behavior

## 5. Security configuration

- [x] 5.1 Configure Spring Session Data Redis (reactive) so dashboard sessions are stored in Redis,
      not process memory — deferred the actual property (`spring.session.store-type: redis`) to
      Group 6, since it belongs in `rls-bootstrap`'s `application.yml` (the module that provides
      the real `ReactiveRedisConnectionFactory` bean); this module's own tests intentionally run
      without it, falling back to the in-memory `WebSession` so they don't need Redis
- [x] 5.2 Configure a `SecurityWebFilterChain` for `/app/**`: form-login backed by
      `LoginTenantUseCase`, session auth, CSRF enabled, `permitAll` for `/app/register` and
      `/app/login`, authenticated for everything else under `/app/**`, logout endpoint
- [x] 5.3 Configure a second `SecurityWebFilterChain` (or explicit `securityMatcher`) for
      `/api/v1/**` that disables CSRF and Spring Security's own auth handling, leaving the existing
      `ApiTokenAuthenticationWebFilter` as the sole auth mechanism for that path, unchanged from
      before this change
- [x] 5.4 Write a slice test asserting an unauthenticated request to a dashboard route redirects to
      `/app/login`, per `web-authentication` — also added a `CsrfModelAttributeAdvice`
      (`@ControllerAdvice` + `@ModelAttribute("_csrf")`), since WebFlux's Thymeleaf integration,
      unlike the servlet stack, doesn't automatically expose the CSRF token as a template variable

## 6. rls-bootstrap wiring

- [x] 6.1 Add `rls-adapter-web` as a dependency of `rls-bootstrap`
- [x] 6.2 Wire `LoginTenantUseCase` as a bean (alongside the existing use-case wiring in
      `UseCaseConfig`) — also wired `GetTenantUseCase` (added in Group 4); added
      `spring.session.store-type: redis` to `application.yml` so the real app uses the Redis-backed
      reactive session repository (design decision 2)
- [x] 6.3 Confirm `RlsApplication`'s component scan picks up the new module's controllers and
      security configuration — `scanBasePackages = "com.ratelimitservice.rls"` already covers
      `com.ratelimitservice.rls.adapter.web`, no change needed; confirmed by running
      `RlsApplicationSmokeIT` against the full stack (Testcontainers Redis + PostgreSQL) — all 3
      tests pass, context starts with both security chains and the Redis session repository active

## 7. End-to-end tests

- [x] 7.1 Write a full happy-path e2e test against the running `@SpringBootTest` context
      (`WebTestClient`, Testcontainers Redis + PostgreSQL, reusing the `AbstractE2ETest` base from
      the `rest-api` change): register via the web form, extract the CSRF token and session cookie,
      log in, create a resource via the dashboard, edit it, delete it, and rotate the API token,
      asserting the dashboard reflects each change
- [x] 7.2 Write an e2e test confirming an unauthenticated dashboard request redirects to
      `/app/login`, and that logging out invalidates the session (a subsequent dashboard request
      redirects again)
- [x] 7.3 Write a regression e2e test confirming the REST API (`/api/v1/**`) still behaves exactly
      as before this change with both security chains active: register a tenant via the REST
      endpoint, create a resource, and call check — no CSRF token or session required, per the
      design's risk mitigation for the two-security-chain split

**Pitfalls hit (all only surfaced once real end-to-end requests were driven through the full
stack — none were visible from compilation or the Group 5 slice test):**
- `ApiTokenAuthenticationWebFilter` (from the `rest-api` change) is a plain `WebFilter` with no path
  scoping of its own; it ran on every request, including `/app/**`, and rejected them with 401 since
  they carry no bearer token. Fixed by having it pass through any path outside `/api/v1/**` (and the
  existing docs prefixes) untouched — it now has no opinion on requests it wasn't built to guard.
  Added a regression test (`passesThroughPathsOutsideApiV1WithoutRequiringAToken`) to
  `ApiTokenAuthenticationWebFilterTest`.
- WebFlux's `@RequestParam`, unlike the servlet stack's unified `request.getParameter()`, only binds
  query parameters — never form-urlencoded POST body data. Every form-backed controller method
  (`RegistrationController.register`, `ResourceDashboardController.create`/`update`) failed with
  `MissingRequestValueException` until switched to a `@ModelAttribute` form-backing bean
  (`RegisterForm`, `CreateResourceForm`, `EditResourceForm`), which WebFlux's data binder does read
  from the request body.
- Spring Security's `.formLogin(form -> form.loginPage("/app/login"))` only *redirects*
  unauthenticated requests to that URL — it doesn't serve the page itself. A `LoginController`
  (`GET /app/login`) was missing entirely; added it.
- Spring Session serializes the whole `SecurityContext` (including the `Authentication` principal)
  to Redis. `TenantPrincipal` originally held the domain `TenantId` record, which isn't
  `Serializable`, throwing `NotSerializableException` on first login. Fixed by having
  `TenantPrincipal` hold a raw `UUID` and reconstruct `TenantId` on access — keeps this
  serialization-infrastructure concern confined to the adapter rather than requiring `rls-domain`'s
  value objects to implement `Serializable`.

## 8. Verification and wrap-up

- [x] 8.1 Run `mvn verify` across every module; confirm every scenario in
      `specs/web-tenant-onboarding/spec.md`, `specs/web-authentication/spec.md`, and
      `specs/web-resource-dashboard/spec.md` is covered by a passing test — full reactor `mvn
      verify` is green; scenarios are covered by `SecurityRedirectTest`,
      `WebOnboardingAndDashboardE2EIT`, `WebAuthenticationE2EIT`, and
      `RestApiUnaffectedRegressionE2EIT`
- [x] 8.2 Update `README.md`: the new module, and how the web dashboard fits alongside the REST API
      when running `rls-bootstrap` locally
- [x] 8.3 Commit work on `feature/thymeleaf-frontend` following git-flow commit conventions (no AI
      co-authorship line)
