## ADDED Requirements

### Requirement: Resource configuration is persisted
The system SHALL persist a newly created `RateLimitResource` such that it can be retrieved after
the originating process ends.

#### Scenario: Saved resource is retrievable by id
- **WHEN** a `RateLimitResource` is saved via `ResourceRepositoryPort`
- **THEN** loading that `ResourceId` through the same port returns a resource with the same
  tenantId, resourceKey, strategyType, quota, fallbackPolicy, and enabled flag

### Requirement: Resource lookup by tenant and resource key
The system SHALL allow finding a persisted `RateLimitResource` by its owning tenant and resource
key.

#### Scenario: Finding an existing resource by tenant and key
- **WHEN** `ResourceRepositoryPort.findByTenantAndKey(tenantId, resourceKey)` is called for a
  previously saved resource
- **THEN** the matching `RateLimitResource` is returned

#### Scenario: Finding a non-existent resource by tenant and key
- **WHEN** `ResourceRepositoryPort.findByTenantAndKey(tenantId, resourceKey)` is called with a key
  that has no saved resource for that tenant
- **THEN** an empty result is returned, not an error

### Requirement: Listing resources by tenant
The system SHALL allow listing every `RateLimitResource` belonging to a given tenant.

#### Scenario: Listing returns only the requesting tenant's resources
- **WHEN** `ResourceRepositoryPort.findAllByTenant(tenantId)` is called
- **THEN** it returns every resource saved for that `tenantId` and none belonging to other tenants

### Requirement: Resource key uniqueness is enforced per tenant
The system SHALL reject persisting a second `RateLimitResource` with a `resourceKey` already used
by another resource of the same tenant, surfacing a domain-meaningful error rather than a
database-specific exception.

#### Scenario: Duplicate resource key for the same tenant is rejected
- **WHEN** a `RateLimitResource` is saved with a `resourceKey` that already belongs to another
  saved resource of the same `tenantId`
- **THEN** the save fails with a domain-meaningful duplicate-resource-key error, and the original
  resource's data is unchanged

#### Scenario: The same resource key is allowed for different tenants
- **WHEN** two `RateLimitResource`s with the same `resourceKey` but different `tenantId`s are saved
- **THEN** both saves succeed

### Requirement: Resource updates are persisted
The system SHALL persist changes made to a `RateLimitResource` (reconfiguration, enabling,
disabling) when it is saved again.

#### Scenario: Reconfigured strategy and quota are persisted
- **WHEN** a saved `RateLimitResource` is reconfigured with a new strategy and quota, saved again,
  and then reloaded
- **THEN** the reloaded resource reflects the new strategy and quota

#### Scenario: Disabled status is persisted
- **WHEN** a saved `RateLimitResource` is disabled, saved again, and then reloaded
- **THEN** the reloaded resource's `enabled` flag is `false`
