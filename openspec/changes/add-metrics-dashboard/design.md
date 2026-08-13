## Context

`docker-compose.yml` (from `add-load-balancer`) runs Traefik in front of a scalable `app` service
(`docker compose up --scale app=N`, no host port mapping, replicas discovered via Docker labels).
Each `app` replica already exposes `/actuator/prometheus` (Micrometer, including
`http_server_requests_seconds_count` per route and the circuit breaker's own metrics — see the
`observability` capability). Traefik's dashboard (`:8080`) shows routing config and per-backend
health (`UP`/`DOWN`) but has no metrics scraping enabled and no request-rate visualization. Nothing
in the stack today aggregates metrics across replicas or renders them.

## Goals / Non-Goals

**Goals:**
- Show total request rate across all `app` instances, and each instance's request rate and
  percentage share of that total, on a dashboard that's up the moment `docker compose up` finishes
  — no manual Grafana configuration.
- Keep scraping meaningful even as the instance count changes (`--scale app=N`), without editing
  any scrape config.

**Non-Goals:**
- No long-term metrics retention or alerting — this is a live, local/demo view, not a production
  monitoring stack.
- No TLS/auth in front of Prometheus or Grafana — same local/dev-only posture already accepted for
  the Traefik dashboard and Actuator endpoints.
- No new application-level metrics — this visualizes what Actuator and Traefik already expose.

## Decisions

1. **Scrape each `app` replica directly via Docker DNS service discovery, not through Traefik.**
   Prometheus's `dns_sd_configs` (type `A`) against the Compose service name `app` resolves to one
   A record per running replica (Docker Compose's embedded DNS behavior for a scaled service) and
   re-resolves on Prometheus's own refresh interval, so scaling with `--scale app=N` is picked up
   automatically. Scraping through Traefik instead was explicitly called out as unreliable in
   `add-load-balancer`'s design: round-robin means a repeated scrape could land on a different
   instance's `/actuator/prometheus` each time, breaking per-instance time series continuity. Each
   discovered target's `instance` label (its container IP:port) is what the dashboard groups by.
2. **Traefik's own Prometheus metrics are scraped too** (`--metrics.prometheus=true`, using
   Traefik's default internal `traefik` entrypoint — the same one already serving the
   dashboard/API on `:8080`, so no new port to publish). This is separate from the per-instance
   `app` metrics: it shows Traefik's own view (requests per router/service, response codes), useful
   as a cross-check against the per-instance sums but not required for the load-percentage
   calculation itself, which comes from the `app` instances' own metrics.
3. **Grafana provisioned entirely as code**: a datasource file
   (`grafana/provisioning/datasources/prometheus.yml`) pointing at the `prometheus` service, and a
   dashboard provider (`grafana/provisioning/dashboards/dashboards.yml`) loading a checked-in
   dashboard JSON (`grafana/dashboards/load-distribution.json`). No manual "add datasource" or
   "import dashboard" steps — matches this project's existing standard of `docker compose up`
   being the single command that gets you to a working, observable system.
4. **Dashboard panels, scoped to exactly what was asked**:
   - Total request rate: `sum(rate(http_server_requests_seconds_count[1m]))`
   - Per-instance request rate: `sum by (instance) (rate(http_server_requests_seconds_count[1m]))`
   - Per-instance load percentage: each instance's rate divided by the total, as a percentage —
     `100 * sum by (instance) (rate(http_server_requests_seconds_count[1m])) / scalar(sum(rate(http_server_requests_seconds_count[1m])))`
   No circuit breaker or rate-limit allow/deny panels in this change — those metrics already exist
   and can be added to the same dashboard later without new infrastructure, but weren't asked for
   here.
5. **Default Grafana credentials** (`admin`/`admin`, `GF_SECURITY_ADMIN_PASSWORD` left at its
   default) — acceptable for this project's local/dev-only posture, same as the unauthenticated
   Traefik dashboard and Actuator endpoints; not for any shared or production deployment.

## Risks / Trade-offs

- [Risk] Docker's embedded DNS returning all A records for a scaled service is standard, documented
  behavior for plain `docker compose` — but it's still a reliance on an implementation detail
  rather than a first-class service-discovery API → Mitigation: acceptable for this project's local
  Compose-only scope; would need Consul/Kubernetes-style SD if this ever targeted a different
  orchestrator.
- [Risk] Prometheus's scrape interval (default 15s, not overridden) means the dashboard lags live
  traffic by up to that interval → Mitigation: acceptable for a load-visualization dashboard, not a
  real-time system; can be tuned in `prometheus.yml` later if needed.
- [Risk] When an `app` replica is added or removed via `--scale`, its time series appears/disappears
  under a new/old `instance` label (container IP changes each time a container is recreated) → not
  a bug, just how Prometheus labels work with ephemeral containers; the dashboard's per-instance
  panel will show a new line rather than relabeling an existing one — documented as expected
  behavior, not fixed in this change.
