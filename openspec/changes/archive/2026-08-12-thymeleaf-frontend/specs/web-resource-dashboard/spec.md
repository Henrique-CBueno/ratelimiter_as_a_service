## ADDED Requirements

### Requirement: Dashboard lists the authenticated tenant's own resources
The system SHALL expose `GET /app/resources`, authenticated, listing only rate-limit resources
owned by the logged-in tenant.

#### Scenario: Dashboard shows only the caller's resources
- **WHEN** an authenticated tenant requests `GET /app/resources`
- **THEN** the response renders a list containing only resources owned by that tenant

### Requirement: Dashboard supports creating a resource
The system SHALL expose a resource-creation form under `/app/resources` that creates a new
resource under the authenticated tenant using the same use case the REST API uses.

#### Scenario: Successful creation redirects to the resource list
- **WHEN** an authenticated tenant submits the creation form with a resource key unique among its
  own resources, a supported strategy type, and a valid quota
- **THEN** the resource is created, and the response redirects to the resource list showing the new
  resource

#### Scenario: Creation rejects a duplicate key for the same tenant
- **WHEN** an authenticated tenant submits the creation form with a resource key it has already
  used
- **THEN** the creation form is re-rendered with an inline error, and no resource is created

### Requirement: Dashboard supports editing a resource's strategy and quota
The system SHALL expose an edit form for a resource owned by the authenticated tenant, updating its
strategy type and quota using the same use case the REST API uses.

#### Scenario: Successful edit redirects to the resource list
- **WHEN** an authenticated tenant submits the edit form for its own resource with a new strategy
  type and quota
- **THEN** the resource is updated, and the response redirects to the resource list reflecting the
  change

### Requirement: Dashboard supports soft-deleting a resource
The system SHALL expose a delete action for a resource owned by the authenticated tenant, disabling
it (`enabled = false`) using the same use case the REST API uses.

#### Scenario: Successful delete redirects to the resource list
- **WHEN** an authenticated tenant triggers delete for its own resource
- **THEN** the resource is disabled, and the response redirects to the resource list, which no
  longer shows that resource as active

### Requirement: Dashboard resource actions are scoped to the owning tenant
The system SHALL treat a resource id that belongs to a different tenant identically to a
nonexistent id for the dashboard's edit and delete actions.

#### Scenario: Editing another tenant's resource is rejected
- **WHEN** an authenticated tenant attempts to edit a resource id owned by a different tenant
- **THEN** the response is a not-found page, and the resource is not modified

### Requirement: Dashboard shows the tenant's API token and supports rotation
The system SHALL expose a page under `/app/settings` (or equivalent) showing the tenant's current
token's display prefix (never the raw token) and an action to rotate it, revealing the newly issued
raw token exactly once using the same use case the REST API uses.

#### Scenario: Rotating the token reveals the new token once
- **WHEN** an authenticated tenant triggers token rotation
- **THEN** the previous token is revoked, the response shows the newly issued raw token exactly
  once, and that token is never shown again on any subsequent page

### Requirement: Dashboard routes are CSRF-protected
The system SHALL reject state-changing dashboard form submissions (create, edit, delete, rotate)
that lack a valid CSRF token.

#### Scenario: A state-changing submission without a valid CSRF token is rejected
- **WHEN** a create, edit, delete, or rotate form is submitted without the CSRF token issued for
  that form
- **THEN** the request is rejected and no state changes
