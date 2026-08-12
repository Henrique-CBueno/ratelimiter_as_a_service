## ADDED Requirements

### Requirement: Session-based login by email and password
The system SHALL expose `GET /app/login` (unauthenticated form) and `POST /app/login`
(unauthenticated submission), authenticating by email and password rather than API token, and
establishing an authenticated session on success.

#### Scenario: Successful login establishes a session
- **WHEN** `POST /app/login` is submitted with the email and password of a registered tenant
- **THEN** the response establishes an authenticated session and redirects to the dashboard

#### Scenario: Invalid credentials are rejected without revealing which field was wrong
- **WHEN** `POST /app/login` is submitted with an email that has no matching tenant, or with a
  password that does not match the tenant's stored password hash
- **THEN** the login form is re-rendered with a generic "invalid email or password" error, not
  disclosing whether the email exists

### Requirement: Sessions are stored outside application memory
The system SHALL persist authenticated dashboard sessions in a shared store (not process-local
memory), so any `rls-bootstrap` instance can serve an authenticated request for an existing
session.

#### Scenario: A session established on one instance is valid on another
- **WHEN** a tenant authenticates and is then routed to a different `rls-bootstrap` instance for a
  subsequent request using the same session
- **THEN** the request is treated as authenticated, without requiring the tenant to log in again

### Requirement: Dashboard routes require an authenticated session
The system SHALL redirect unauthenticated requests to any `/app/**` route other than the public
registration and login pages to the login page.

#### Scenario: Unauthenticated dashboard access redirects to login
- **WHEN** a request without an authenticated session is made to a dashboard route under `/app/**`
- **THEN** the response redirects to `/app/login`, and no tenant data is rendered

### Requirement: Logout ends the session
The system SHALL expose a logout action that invalidates the current session and redirects to the
login page.

#### Scenario: Logout invalidates the session
- **WHEN** an authenticated tenant triggers logout
- **THEN** the session is invalidated, and a subsequent request to a dashboard route redirects to
  `/app/login`

### Requirement: The REST API's token authentication is unaffected
The system SHALL continue to authenticate `/api/v1/**` requests exclusively via the existing
`Authorization: Bearer` token mechanism, independent of the new session-based web authentication.

#### Scenario: REST API requests are unaffected by the web login mechanism
- **WHEN** a request to `/api/v1/**` is made with a valid `Authorization: Bearer` token and no
  session cookie
- **THEN** the request is authenticated exactly as it was before this change, and is not redirected
  to `/app/login`
