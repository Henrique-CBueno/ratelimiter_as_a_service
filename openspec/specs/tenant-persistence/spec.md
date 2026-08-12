# tenant-persistence Specification

## Purpose
TBD - created by syncing change postgres-adapter. Update Purpose after archive.

## Requirements

### Requirement: Tenant registration is persisted
The system SHALL persist a newly registered `Tenant` such that it can be retrieved by its
`TenantId` after the originating process ends.

#### Scenario: Saved tenant is retrievable by id
- **WHEN** a `Tenant` is saved via `TenantRepositoryPort`
- **THEN** loading that `TenantId` through the same port returns a `Tenant` with the same name,
  email, password hash, status, and default fallback policy

### Requirement: Tenant lookup by email
The system SHALL allow finding a persisted `Tenant` by its email address.

#### Scenario: Finding an existing tenant by email
- **WHEN** `TenantRepositoryPort.findByEmail(email)` is called with the email of a saved tenant
- **THEN** the matching `Tenant` is returned

#### Scenario: Finding a non-existent tenant by email
- **WHEN** `TenantRepositoryPort.findByEmail(email)` is called with an email that has no saved tenant
- **THEN** an empty result is returned, not an error

### Requirement: Email uniqueness is enforced
The system SHALL reject persisting a second `Tenant` with an email already used by an existing
tenant.

#### Scenario: Duplicate email is rejected
- **WHEN** a `Tenant` is saved with an email that already belongs to a previously saved tenant
- **THEN** the save fails with a domain-meaningful duplicate-email error, and the original tenant's
  data is unchanged

### Requirement: API tokens are persisted with the tenant
The system SHALL persist a `Tenant`'s `ApiToken`s (issued and rotated) as part of saving the tenant,
including which token is active.

#### Scenario: Issued token is retrievable after reload
- **WHEN** a `Tenant` issues an `ApiToken` and is saved, then reloaded by `TenantId`
- **THEN** the reloaded tenant's token list contains a token with the same hash, prefix, and active
  status as the one issued before saving

#### Scenario: Rotated token's revocation is persisted
- **WHEN** a `Tenant` rotates its `ApiToken` and is saved, then reloaded by `TenantId`
- **THEN** the reloaded tenant shows the previous token as revoked and the new token as active

### Requirement: Tenant lookup by active API token hash
The system SHALL allow finding the `Tenant` that owns a given active (non-revoked) API token hash,
to support authenticating incoming requests by token in later specs.

#### Scenario: Finding the tenant for an active token hash
- **WHEN** `TenantRepositoryPort.findByActiveTokenHash(tokenHash)` is called with the hash of a
  currently active token
- **THEN** the owning `Tenant` is returned

#### Scenario: A revoked token hash does not resolve to a tenant
- **WHEN** `TenantRepositoryPort.findByActiveTokenHash(tokenHash)` is called with the hash of a
  token that has since been revoked or rotated away
- **THEN** an empty result is returned

### Requirement: Credential hashing never stores or returns plaintext
The system SHALL provide a `SecretHasherPort` (used for both tenant passwords and API tokens) that
turns a raw secret into a verifiable hash without the hash allowing recovery of the original
secret, and a matching verification operation.

#### Scenario: A raw secret's hash verifies successfully against that same secret
- **WHEN** a raw secret is hashed via `SecretHasherPort.hash(rawSecret)` and then checked via
  `SecretHasherPort.matches(rawSecret, hash)`
- **THEN** the match succeeds

#### Scenario: A hash does not verify against a different secret
- **WHEN** `SecretHasherPort.matches(differentRawSecret, hash)` is called with a secret other
  than the one originally hashed
- **THEN** the match fails
