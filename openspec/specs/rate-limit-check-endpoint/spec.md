# rate-limit-check-endpoint Specification

## Purpose
TBD - created by syncing change rest-api. Update Purpose after archive.

## Requirements

### Requirement: Check endpoint requires authentication
The system SHALL reject `POST /api/v1/ratelimit/check` requests that lack a valid `Authorization:
Bearer` token.

#### Scenario: Unauthenticated check is rejected
- **WHEN** `POST /api/v1/ratelimit/check` is called without a valid `Authorization: Bearer` token
- **THEN** the response is `401 Unauthorized` and no evaluation is performed

### Requirement: Check endpoint evaluates against the caller's configured resource
The system SHALL accept a resource key and a client IP in the request body, resolve the matching
resource configured under the authenticated tenant, and evaluate it via the strategy configured for
that resource.

#### Scenario: Checking an unconfigured resource
- **WHEN** an authenticated tenant calls `POST /api/v1/ratelimit/check` with a `resource` value it
  has not configured
- **THEN** the response is `404 Not Found`

### Requirement: Allowed requests return 200 with rate-limit metadata
The system SHALL respond `200 OK` when the evaluation allows the request, with a body containing
`allowed`, `limit`, `remaining`, `resetAt`, and `strategy`, and `RateLimit-Limit`,
`RateLimit-Remaining`, and `RateLimit-Reset` response headers.

#### Scenario: Allowed check
- **WHEN** an authenticated tenant calls `POST /api/v1/ratelimit/check` for a configured resource
  and client IP still within its configured limit
- **THEN** the response is `200 OK` with `allowed = true` in the body and the `RateLimit-*` headers
  present

### Requirement: Denied requests return 429 with retry guidance
The system SHALL respond `429 Too Many Requests` when the evaluation denies the request, with a
`Retry-After` header and `retryAfterSeconds` in the body.

#### Scenario: Denied check
- **WHEN** an authenticated tenant calls `POST /api/v1/ratelimit/check` for a configured resource
  and client IP that has exceeded its configured limit
- **THEN** the response is `429 Too Many Requests` with `allowed = false`, a non-null
  `retryAfterSeconds` in the body, and a `Retry-After` header
