# load-balancing Specification

## Purpose
TBD - created by archiving change add-load-balancer. Update Purpose after archive.
## Requirements
### Requirement: Requests are routed through a single load-balanced entrypoint
The system SHALL provide a single HTTP entrypoint (Traefik) in front of all application instances, so clients never need to address an individual instance directly.

#### Scenario: Client reaches the application without knowing instance topology
- **WHEN** a client sends an HTTP request to the load balancer's entrypoint (port 80) without specifying which application instance should handle it
- **THEN** the request is routed to one of the currently healthy application instances and served successfully

### Requirement: Traffic is distributed across instances via round robin
The system SHALL distribute incoming requests across all healthy application instances using round-robin distribution, without session affinity.

#### Scenario: Requests are spread across multiple instances
- **WHEN** a series of requests is sent through the load balancer's entrypoint while more than one application instance is running
- **THEN** the requests are distributed across the running instances rather than all being served by a single one

### Requirement: Unhealthy instances are automatically removed from routing
The system SHALL stop routing requests to an application instance that fails its health check, and resume routing to it once it becomes healthy again.

#### Scenario: An unhealthy instance stops receiving traffic
- **WHEN** an application instance's `/actuator/health` endpoint stops reporting a healthy status
- **THEN** the load balancer stops routing new requests to that instance within the configured health-check interval

#### Scenario: A recovered instance resumes receiving traffic
- **WHEN** a previously unhealthy application instance's `/actuator/health` endpoint reports a healthy status again
- **THEN** the load balancer resumes routing requests to that instance within the configured health-check interval

### Requirement: The application scales without manual load balancer reconfiguration
The system SHALL automatically discover new application instances added to the Compose topology and begin routing traffic to them, without any manual load balancer configuration change.

#### Scenario: Scaling up adds capacity automatically
- **WHEN** the application is scaled to more instances (e.g. `docker compose up --scale app=N`)
- **THEN** the load balancer begins routing requests to the new instances without any change to the load balancer's configuration files

