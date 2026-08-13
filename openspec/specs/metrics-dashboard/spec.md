# metrics-dashboard Specification

## Purpose
TBD - created by archiving change add-metrics-dashboard. Update Purpose after archive.
## Requirements
### Requirement: Prometheus scrapes every application instance
The system SHALL run a Prometheus server that discovers and scrapes every running `app` replica's
`/actuator/prometheus` endpoint, automatically picking up replicas added or removed by scaling.

#### Scenario: All running instances are scraped
- **WHEN** the Compose topology is running with more than one `app` replica (e.g.
  `docker compose up --scale app=3`)
- **THEN** Prometheus's target list includes each running replica, and querying Prometheus for
  `http_server_requests_seconds_count` returns a distinct time series per instance

#### Scenario: Scaling is picked up without config changes
- **WHEN** the number of `app` replicas changes via `docker compose up --scale app=N`
- **THEN** Prometheus's scraped target list reflects the new replica count within its next
  scrape/discovery cycle, with no edits to `prometheus.yml`

### Requirement: Prometheus scrapes Traefik's own metrics
The system SHALL configure Prometheus to scrape Traefik's Prometheus metrics endpoint in addition
to the application instances.

#### Scenario: Traefik metrics are queryable
- **WHEN** Prometheus has completed at least one scrape cycle
- **THEN** Traefik's own request/router metrics are queryable in Prometheus

### Requirement: A pre-provisioned dashboard shows total and per-instance load
The system SHALL ship a Grafana dashboard, provisioned automatically at startup, showing total
request rate across all application instances and each instance's request rate and percentage
share of that total.

#### Scenario: Dashboard is available without manual setup
- **WHEN** `docker compose up` has finished starting the `grafana` service
- **THEN** the load-distribution dashboard is already present in Grafana, with its Prometheus
  datasource already configured, requiring no manual "add datasource" or "import dashboard" steps

#### Scenario: Dashboard reflects live per-instance load
- **WHEN** requests are sent to the application through the Traefik entrypoint while more than one
  `app` replica is running
- **THEN** the dashboard's total request rate panel reflects the combined rate across instances,
  and the per-instance panel shows each instance's individual rate and its percentage share of the
  total

