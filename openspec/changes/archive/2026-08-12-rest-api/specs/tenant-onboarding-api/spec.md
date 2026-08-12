## ADDED Requirements

### Requirement: Tenant registration endpoint
The system SHALL expose `POST /api/v1/tenants`, unauthenticated, accepting a name, email, and
password, and returning the new tenant's id and a newly issued API token.

#### Scenario: Successful registration
- **WHEN** `POST /api/v1/tenants` is called with a valid name, a well-formed unused email, and a
  non-blank password
- **THEN** the response is `201 Created` with a body containing `tenantId` and `apiToken`, and the
  `apiToken` value is never persisted anywhere in plaintext

#### Scenario: Registration rejects a duplicate email
- **WHEN** `POST /api/v1/tenants` is called with an email already used by a previously registered
  tenant
- **THEN** the response is `409 Conflict` with a `ProblemDetail` body, and no new tenant is created

### Requirement: API token rotation endpoint
The system SHALL expose `POST /api/v1/tokens/rotate`, authenticated, which revokes the caller's
current active token and issues a new one.

#### Scenario: Successful rotation
- **WHEN** an authenticated tenant calls `POST /api/v1/tokens/rotate`
- **THEN** the response is `200 OK` with a body containing the new `apiToken`, the previous token
  is no longer accepted for authentication, and the new token is accepted for subsequent requests

#### Scenario: Rotation requires authentication
- **WHEN** `POST /api/v1/tokens/rotate` is called without a valid `Authorization: Bearer` token
- **THEN** the response is `401 Unauthorized` and no token is rotated
