## Why

Tenants currently have no way to manage their account without calling the REST API directly
(curl/Postman): registering, logging in, and configuring rate-limit resources all require raw HTTP
calls and manual handling of the API token. Spec 5 of the architecture plan adds a server-rendered
Thymeleaf front end so a tenant can self-onboard and manage their resources through a browser.

## What Changes

- Add a new `rls-adapter-web` module: server-rendered Thymeleaf pages served by the same
  `rls-bootstrap` application, reusing the existing `rls-application` use cases directly (not by
  calling the REST API over HTTP).
- Add a public registration page that calls `RegisterTenantUseCase` and shows the newly issued API
  token exactly once (mirroring `POST /api/v1/tenants`'s one-time-reveal behavior).
- Add session-based login/logout via Spring Security form-login, authenticating by email + password
  — a new mechanism, distinct from the REST API's `Authorization: Bearer` token auth, since a human
  at a browser has a password, not a token. Add a `LoginTenantUseCase` in `rls-application` for
  this (email + password → `Tenant`), separate from the existing token-based
  `AuthenticateTenantUseCase` used by the REST API.
- Add CSRF protection for all state-changing form submissions.
- Add an authenticated dashboard: list/create/edit/(soft-)delete rate-limit resources for the
  logged-in tenant, and view/rotate the tenant's API token — reusing the same resource and token
  use cases the REST API uses (`CreateResourceUseCase`, `UpdateResourceUseCase`,
  `DeleteResourceUseCase`, `ListResourcesUseCase`, `RotateApiTokenUseCase`).
- Wire the new module into `rls-bootstrap` alongside the existing REST API; both are served by the
  same application, on different URL paths, with independent auth mechanisms (session cookie for
  `/app/**`, bearer token for `/api/v1/**`).

## Capabilities

### New Capabilities
- `web-tenant-onboarding`: public self-registration page that creates a tenant and reveals its API
  token once.
- `web-authentication`: session-based login/logout for the dashboard, authenticating by email and
  password, with CSRF-protected forms and unauthenticated redirects to the login page.
- `web-resource-dashboard`: authenticated dashboard for a tenant to list, create, edit, and
  soft-delete its rate-limit resources, and to view and rotate its API token.

### Modified Capabilities
(none — the web front end reuses existing `rls-application` use cases and ports as-is; no existing
requirement changes)

## Impact

- New module `rls-adapter-web` (Thymeleaf templates, Spring MVC-style controllers or WebFlux
  equivalents, Spring Security form-login configuration).
- New use case `LoginTenantUseCase` in `rls-application` (email + password authentication),
  alongside a small unit test suite.
- `rls-bootstrap`: adds `rls-adapter-web` as a dependency, wires its beans, adds Spring Security
  configuration scoping session auth to `/app/**` and leaving `/api/v1/**` on the existing
  `ApiTokenAuthenticationWebFilter`.
- No changes to `rls-domain`, existing REST controllers, or existing persistence/Redis adapters.
