## Why

Each application instance already exposes raw Prometheus-formatted metrics via
`/actuator/prometheus`, and Traefik's dashboard shows per-instance health status — but nothing
scrapes, aggregates, or visualizes any of it. There is no way today to see request load per
instance or how it's distributed across the load-balanced pool without manually curling each
instance's metrics endpoint and doing the math by hand.

## What Changes

- Enable Traefik's built-in Prometheus metrics (`--metrics.prometheus=true`), exposing
  router/service-level request counts alongside the existing dashboard.
- Add a `prometheus` service to `docker-compose.yml`, configured via a checked-in `prometheus.yml`
  to scrape Traefik's `/metrics` endpoint and every `app` replica's `/actuator/prometheus` directly
  (via Docker's DNS-based service discovery, which returns one A record per replica for a scaled
  Compose service — this is what actually makes per-instance breakdown possible, since scraping
  through Traefik would just show whichever instance happened to answer that request).
- Add a `grafana` service, pre-provisioned (datasource + dashboard shipped as files, not manual
  clicking) with a dashboard showing: total request rate across all instances, request rate
  per instance, and each instance's percentage share of total load.
- Document local usage (`docker compose up --scale app=N`, where to find the dashboard) in
  `README.md`.

## Capabilities

### New Capabilities
- `metrics-dashboard`: Prometheus scraping every app instance and Traefik, and a pre-provisioned
  Grafana dashboard showing total and per-instance request load as counts and percentages.

### Modified Capabilities
(none — purely additive: new services consuming metrics endpoints that already exist, no change to
what `observability` or `load-balancing` themselves expose or require)

## Impact

- `docker-compose.yml`: `traefik` service gains a metrics flag; new `prometheus` and `grafana`
  services.
- New `prometheus.yml` (scrape config) and `grafana/provisioning/**` (datasource + dashboard JSON)
  files at the repo root.
- `README.md`: new section on where to find the dashboard and what it shows.
- No application code changes — this only adds infrastructure consuming metrics endpoints that
  already exist (`/actuator/prometheus`, Traefik's dashboard entrypoint).
