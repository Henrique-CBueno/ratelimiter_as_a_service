## MODIFIED Requirements

### Requirement: Docker Compose runs multiple stateless application instances against shared state
The system SHALL support running more than one application instance in the same Compose topology,
sharing the same Redis and PostgreSQL, to demonstrate that rate-limit state is coordinated through
the shared backing services rather than any single instance's memory. Instances SHALL be reachable
only through a single load-balanced entrypoint, not through individually published host ports.

#### Scenario: Multiple instances enforce a shared rate limit correctly
- **WHEN** requests for the same tenant, resource, and client IP are sent through the load-balanced
  entrypoint to a Compose topology scaled to more than one application instance, exceeding the
  resource's configured limit
- **THEN** the combined count of allowed requests across all instances does not exceed the
  configured limit, exactly as if all requests had gone to a single instance

#### Scenario: Instances are not individually addressable by host port
- **WHEN** the Compose topology is started with `docker compose up --scale app=N`
- **THEN** no individual application instance publishes its own host port; the only way to reach the
  application from outside the Compose network is through the load balancer's entrypoint
