## 1. Setup

- [ ] 1.1 Create the `rls-adapter-web` module (`pom.xml` depending on `rls-domain`, `rls-application`,
      `spring-boot-starter-webflux`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-security`,
      `spring-session-data-redis`, `spring-boot-starter-test`, `reactor-test`); add it to the parent
      `pom.xml`'s `<modules>`
- [ ] 1.2 Verify the module compiles with an empty `src/main/java` package placeholder

## 2. Application use case: login by email and password

- [ ] 2.1 Write a failing test for `LoginTenantUseCase.login(email, password)` returning the
      matching `Tenant` when the email exists and the password matches (using the existing
      in-memory `TenantRepositoryPort` fake and `FakeSecretHasherPort` from `rls-application`'s test
      sources), then implement it to pass
- [ ] 2.2 Write a failing test for login failing (empty `Mono`, not an exception) when the email has
      no matching tenant, then confirm it passes
- [ ] 2.3 Write a failing test for login failing when the email matches but the password does not,
      then confirm it passes

## 3. Thymeleaf templates

- [ ] 3.1 Create a base layout fragment (nav, flash-message region) reused by every page
- [ ] 3.2 Create the registration page template and its post-submission "token revealed once" page
- [ ] 3.3 Create the login page template
- [ ] 3.4 Create the resource list (dashboard) template, the create/edit resource form template, and
      a not-found template for cross-tenant resource access
- [ ] 3.5 Create the settings/token page template, including its "token revealed once" state after
      rotation

## 4. Web controllers

- [ ] 4.1 Implement the registration controller (`GET`/`POST /app/register`), calling
      `RegisterTenantUseCase`, rendering the duplicate-email case as an inline form error per
      `web-tenant-onboarding`
- [ ] 4.2 Implement the resource dashboard controller (`GET /app/resources`, create, edit, delete),
      calling the same use cases the REST API uses (`ListResourcesUseCase`, `CreateResourceUseCase`,
      `UpdateResourceUseCase`, `DeleteResourceUseCase`), scoped to the authenticated tenant from the
      session
- [ ] 4.3 Implement the settings/token controller (view token prefix, rotate), calling
      `RotateApiTokenUseCase`
- [ ] 4.4 Implement cross-tenant resource access as a not-found response (edit/delete for another
      tenant's resource id), matching the REST API's existing `resource-management-api` behavior

## 5. Security configuration

- [ ] 5.1 Configure Spring Session Data Redis (reactive) so dashboard sessions are stored in Redis,
      not process memory
- [ ] 5.2 Configure a `SecurityWebFilterChain` for `/app/**`: form-login backed by
      `LoginTenantUseCase`, session auth, CSRF enabled, `permitAll` for `/app/register` and
      `/app/login`, authenticated for everything else under `/app/**`, logout endpoint
- [ ] 5.3 Configure a second `SecurityWebFilterChain` (or explicit `securityMatcher`) for
      `/api/v1/**` that disables CSRF and Spring Security's own auth handling, leaving the existing
      `ApiTokenAuthenticationWebFilter` as the sole auth mechanism for that path, unchanged from
      before this change
- [ ] 5.4 Write a slice test asserting an unauthenticated request to a dashboard route redirects to
      `/app/login`, per `web-authentication`

## 6. rls-bootstrap wiring

- [ ] 6.1 Add `rls-adapter-web` as a dependency of `rls-bootstrap`
- [ ] 6.2 Wire `LoginTenantUseCase` as a bean (alongside the existing use-case wiring in
      `UseCaseConfig`)
- [ ] 6.3 Confirm `RlsApplication`'s component scan picks up the new module's controllers and
      security configuration

## 7. End-to-end tests

- [ ] 7.1 Write a full happy-path e2e test against the running `@SpringBootTest` context
      (`WebTestClient`, Testcontainers Redis + PostgreSQL, reusing the `AbstractE2ETest` base from
      the `rest-api` change): register via the web form, extract the CSRF token and session cookie,
      log in, create a resource via the dashboard, edit it, delete it, and rotate the API token,
      asserting the dashboard reflects each change
- [ ] 7.2 Write an e2e test confirming an unauthenticated dashboard request redirects to
      `/app/login`, and that logging out invalidates the session (a subsequent dashboard request
      redirects again)
- [ ] 7.3 Write a regression e2e test confirming the REST API (`/api/v1/**`) still behaves exactly
      as before this change with both security chains active: register a tenant via the REST
      endpoint, create a resource, and call check — no CSRF token or session required, per the
      design's risk mitigation for the two-security-chain split

## 8. Verification and wrap-up

- [ ] 8.1 Run `mvn verify` across every module; confirm every scenario in
      `specs/web-tenant-onboarding/spec.md`, `specs/web-authentication/spec.md`, and
      `specs/web-resource-dashboard/spec.md` is covered by a passing test
- [ ] 8.2 Update `README.md`: the new module, and how the web dashboard fits alongside the REST API
      when running `rls-bootstrap` locally
- [ ] 8.3 Commit work on `feature/thymeleaf-frontend` following git-flow commit conventions (no AI
      co-authorship line)
