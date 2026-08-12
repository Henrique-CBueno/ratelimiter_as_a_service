# tenant-management Specification

## Purpose
TBD - created by archiving change setup-domain-core. Update Purpose after archive.

## Requirements

### Requirement: Tenant registration
The system SHALL allow a new `Tenant` aggregate to be created with a name, a unique email, a
hashed credential, and a default fallback policy, starting in `ACTIVE` status.

#### Scenario: Valid tenant registration
- **WHEN** a `Tenant` is created with a non-blank name, a valid email, a non-blank hashed
  credential, and a `defaultFallbackPolicy` of `FAIL_OPEN` or `FAIL_CLOSED`
- **THEN** the aggregate is created with `status = ACTIVE` and a newly generated `TenantId`

#### Scenario: Registration rejected with invalid email
- **WHEN** a `Tenant` is created with an email that does not match a valid email format
- **THEN** the domain rejects the creation with a validation error

### Requirement: Tenant credentials are never stored in plain text
The system SHALL only accept and store a pre-hashed credential value for a `Tenant`; the domain
layer SHALL NOT perform hashing itself and SHALL NOT expose the raw credential once set.

#### Scenario: Password hash is stored, not the raw password
- **WHEN** a `Tenant` is constructed with a `passwordHash` value
- **THEN** the aggregate exposes only the hash (no method returns or reconstructs a plaintext password)

### Requirement: Tenant status lifecycle
The system SHALL support transitioning a `Tenant` between `ACTIVE` and `SUSPENDED` status.

#### Scenario: Suspending an active tenant
- **WHEN** `suspend()` is called on a `Tenant` with `status = ACTIVE`
- **THEN** the tenant's status becomes `SUSPENDED`

#### Scenario: Reactivating a suspended tenant
- **WHEN** `reactivate()` is called on a `Tenant` with `status = SUSPENDED`
- **THEN** the tenant's status becomes `ACTIVE`

### Requirement: API token issuance
The system SHALL allow issuing a new `ApiToken` for a `Tenant`, storing only its hash and a
display prefix, never the raw token value.

#### Scenario: Issuing the first token for a tenant
- **WHEN** `issueApiToken()` is called on a `Tenant`
- **THEN** a new `ApiToken` is created with a `tokenHash`, a `tokenPrefix`, `createdAt` set, and `revokedAt` null

### Requirement: API token rotation
The system SHALL allow rotating a `Tenant`'s API token by revoking the currently active token and
issuing a new one, without ever exposing the previous raw token again.

#### Scenario: Rotating an active token
- **WHEN** `rotateApiToken()` is called on a `Tenant` that has an active (non-revoked) `ApiToken`
- **THEN** the previous token's `revokedAt` is set to the current instant and a new `ApiToken` is issued

#### Scenario: Rotating when no active token exists
- **WHEN** `rotateApiToken()` is called on a `Tenant` with no active `ApiToken`
- **THEN** a new `ApiToken` is issued without attempting to revoke anything

### Requirement: Default fallback policy
Every `Tenant` SHALL declare a `defaultFallbackPolicy` (`FAIL_OPEN` or `FAIL_CLOSED`) used by any
`RateLimitResource` that does not define its own fallback policy.

#### Scenario: Default policy is set at registration
- **WHEN** a `Tenant` is registered with `defaultFallbackPolicy = FAIL_CLOSED`
- **THEN** the aggregate stores and exposes `FAIL_CLOSED` as its default fallback policy
