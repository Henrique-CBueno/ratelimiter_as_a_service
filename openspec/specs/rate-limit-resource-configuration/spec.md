# rate-limit-resource-configuration Specification

## Purpose
TBD - created by archiving change setup-domain-core. Update Purpose after archive.

## Requirements

### Requirement: Resource creation with strategy and quota
The system SHALL allow creating a `RateLimitResource` aggregate that belongs to a `Tenant`
(referenced by `TenantId`), with a `resourceKey`, a `StrategyType`, and a `Quota` (limit, window
duration, and optional burst capacity).

#### Scenario: Valid resource creation
- **WHEN** a `RateLimitResource` is created with a non-blank `resourceKey`, a supported
  `StrategyType`, and a `Quota` with `limit > 0` and `window > 0`
- **THEN** the aggregate is created with a new `ResourceId`, `enabled = true`, and the given configuration

#### Scenario: Creation rejected with non-positive quota
- **WHEN** a `RateLimitResource` is created with a `Quota` where `limit <= 0` or `window <= 0`
- **THEN** the domain rejects the creation with a validation error

### Requirement: Resource key uniqueness per tenant
Within a single `Tenant`, each `resourceKey` SHALL be unique among enabled resources; enforcement
of this uniqueness across the full resource collection is an application-layer responsibility
backed by a database constraint, but the domain SHALL expose the fields needed to check it.

#### Scenario: Domain exposes tenant and resource key for uniqueness checks
- **WHEN** a `RateLimitResource` is constructed
- **THEN** it exposes both `tenantId` and `resourceKey` so the application layer can verify no other enabled resource shares the same pair

### Requirement: Fallback policy inheritance
A `RateLimitResource` SHALL allow its `fallbackPolicy` to be left unset (null); when unset, the
resource is considered to inherit the owning tenant's `defaultFallbackPolicy`.

#### Scenario: Resource without explicit fallback policy inherits from tenant
- **WHEN** a `RateLimitResource` is created without a `fallbackPolicy`
- **THEN** `resource.fallbackPolicy` is null and callers resolving the effective policy SHALL fall back to `tenant.defaultFallbackPolicy`

#### Scenario: Resource with explicit fallback policy overrides the tenant default
- **WHEN** a `RateLimitResource` is created with an explicit `fallbackPolicy` of `FAIL_OPEN`
- **THEN** `resource.fallbackPolicy` is `FAIL_OPEN` regardless of the tenant's default

### Requirement: Resource enable and disable
The system SHALL allow disabling and re-enabling a `RateLimitResource` without deleting it
(soft delete), preserving its configuration and identity.

#### Scenario: Disabling an enabled resource
- **WHEN** `disable()` is called on a `RateLimitResource` with `enabled = true`
- **THEN** the resource's `enabled` becomes `false`, retaining all other fields

#### Scenario: Re-enabling a disabled resource
- **WHEN** `enable()` is called on a `RateLimitResource` with `enabled = false`
- **THEN** the resource's `enabled` becomes `true`

### Requirement: Strategy reconfiguration
The system SHALL allow changing the `StrategyType` and `Quota` of an existing `RateLimitResource`
without changing its `ResourceId` or `resourceKey`.

#### Scenario: Changing strategy and quota
- **WHEN** `reconfigure(newStrategyType, newQuota)` is called on a `RateLimitResource`
- **THEN** the resource's `strategyType` and `quota` are updated while `id`, `tenantId`, and `resourceKey` remain unchanged

#### Scenario: Reconfiguration rejected with invalid quota
- **WHEN** `reconfigure(newStrategyType, newQuota)` is called with a `newQuota` where `limit <= 0` or `window <= 0`
- **THEN** the domain rejects the change with a validation error and the resource keeps its previous configuration
