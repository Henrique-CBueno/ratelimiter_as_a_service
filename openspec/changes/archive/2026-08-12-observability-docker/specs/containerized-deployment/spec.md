## ADDED Requirements

### Requirement: The application builds as a container image
The system SHALL provide a multi-stage `Dockerfile` that builds `rls-bootstrap` and its module
dependencies into a runnable container image, without requiring a pre-built jar on the host.

#### Scenario: Building the image from a clean checkout succeeds
- **WHEN** `docker build` is run against the repository root on a machine with only Docker
  installed (no local Maven/JDK setup required)
- **THEN** the build completes successfully and produces a runnable image

### Requirement: Docker Compose starts the application with its backing services
The system SHALL provide a `docker-compose.yml` that starts Redis, PostgreSQL, and the application,
wired together with no manual configuration.

#### Scenario: Compose startup succeeds and the application is reachable
- **WHEN** `docker compose up` is run from a clean state
- **THEN** Redis, PostgreSQL, and the application all start successfully, and the application's
  `/actuator/health` endpoint reports `UP` once dependencies are ready

### Requirement: Docker Compose runs multiple stateless application instances against shared state
The system SHALL support running more than one application instance in the same Compose topology,
sharing the same Redis and PostgreSQL, to demonstrate that rate-limit state is coordinated through
the shared backing services rather than any single instance's memory.

#### Scenario: Two instances enforce a shared rate limit correctly
- **WHEN** requests for the same tenant, resource, and client IP are sent alternately to two
  different application instances in the same Compose topology, exceeding the resource's configured
  limit
- **THEN** the combined count of allowed requests across both instances does not exceed the
  configured limit, exactly as if all requests had gone to a single instance
