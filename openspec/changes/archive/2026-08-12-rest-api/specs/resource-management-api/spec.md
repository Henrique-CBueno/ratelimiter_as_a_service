## ADDED Requirements

### Requirement: Resource management endpoints require authentication
The system SHALL reject every resource management request (`POST/GET/PUT/DELETE
/api/v1/resources...`) that lacks a valid `Authorization: Bearer` token, before any resource logic
runs.

#### Scenario: Unauthenticated request is rejected
- **WHEN** any resource management endpoint is called without a valid `Authorization: Bearer` token
- **THEN** the response is `401 Unauthorized`

### Requirement: Resource creation endpoint
The system SHALL expose `POST /api/v1/resources`, authenticated, accepting a resource key, strategy
type, and quota, creating the resource under the caller's tenant.

#### Scenario: Successful creation
- **WHEN** an authenticated tenant calls `POST /api/v1/resources` with a resource key unique among
  its own resources, a supported strategy type, and a valid quota
- **THEN** the response is `201 Created` with the new resource's representation, owned by the
  caller's tenant

#### Scenario: Creation rejects a duplicate key for the same tenant
- **WHEN** an authenticated tenant calls `POST /api/v1/resources` with a resource key it has
  already used
- **THEN** the response is `409 Conflict` with a `ProblemDetail` body

### Requirement: Listing resources is scoped to the authenticated tenant
The system SHALL expose `GET /api/v1/resources`, returning only resources owned by the
authenticated tenant.

#### Scenario: Listing returns only the caller's own resources
- **WHEN** an authenticated tenant calls `GET /api/v1/resources`
- **THEN** the response is `200 OK` with a list containing only resources owned by that tenant, and
  no resources owned by other tenants

### Requirement: Reading, updating, and deleting a resource are scoped to its owning tenant
The system SHALL treat a resource id that belongs to a different tenant identically to a
nonexistent id for `GET`, `PUT`, and `DELETE /api/v1/resources/{id}`.

#### Scenario: Reading another tenant's resource returns not found
- **WHEN** an authenticated tenant calls `GET /api/v1/resources/{id}` for a resource id owned by a
  different tenant
- **THEN** the response is `404 Not Found`, identical to requesting a nonexistent id

#### Scenario: Updating another tenant's resource returns not found
- **WHEN** an authenticated tenant calls `PUT /api/v1/resources/{id}` for a resource id owned by a
  different tenant
- **THEN** the response is `404 Not Found` and the resource is not modified

#### Scenario: Deleting another tenant's resource returns not found
- **WHEN** an authenticated tenant calls `DELETE /api/v1/resources/{id}` for a resource id owned by
  a different tenant
- **THEN** the response is `404 Not Found` and the resource is not disabled

### Requirement: Updating a resource reconfigures its strategy and quota
The system SHALL allow `PUT /api/v1/resources/{id}` to change the strategy type and quota of a
resource owned by the authenticated tenant.

#### Scenario: Successful update
- **WHEN** an authenticated tenant calls `PUT /api/v1/resources/{id}` for its own resource with a
  new strategy type and quota
- **THEN** the response is `200 OK` with the updated resource representation, and a subsequent
  `GET` for the same id reflects the change

### Requirement: Deleting a resource is a soft delete
The system SHALL treat `DELETE /api/v1/resources/{id}` as disabling the resource
(`enabled = false`), not removing its stored configuration.

#### Scenario: Successful delete disables the resource
- **WHEN** an authenticated tenant calls `DELETE /api/v1/resources/{id}` for its own resource
- **THEN** the response is `204 No Content`, and a subsequent `GET` for the same id still returns
  the resource with `enabled = false`
