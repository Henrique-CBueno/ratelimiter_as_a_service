## 1. Traefik metrics

- [x] 1.1 Add `--metrics.prometheus=true` to the `traefik` service's command flags in `docker-compose.yml`

## 2. Prometheus

- [x] 2.1 Create `prometheus.yml` at repo root: scrape job for `app` replicas using `dns_sd_configs` (type `A`, name `app`, port `8080`), `metrics_path: /actuator/prometheus`
- [x] 2.2 Add a second scrape job in `prometheus.yml` for Traefik (`static_configs` target `traefik:8080`, `metrics_path: /metrics`)
- [x] 2.3 Add a `prometheus` service to `docker-compose.yml` (official `prom/prometheus` image), mounting `prometheus.yml`, `depends_on: traefik`

## 3. Grafana

- [x] 3.1 Create `grafana/provisioning/datasources/prometheus.yml` (Prometheus datasource pointing at the `prometheus` service)
- [x] 3.2 Create `grafana/provisioning/dashboards/dashboards.yml` (dashboard provider loading from `grafana/dashboards/`)
- [x] 3.3 Create `grafana/dashboards/load-distribution.json`: total request rate panel, per-instance request rate panel, per-instance load percentage panel
- [x] 3.4 Add a `grafana` service to `docker-compose.yml` (official `grafana/grafana` image), mounting the provisioning directory, publishing port `3000`, `depends_on: prometheus`

## 4. Verification

- [x] 4.1 `docker compose up --scale app=3` — confirm Prometheus's target list (`/targets`) shows all 3 `app` replicas plus Traefik, all `UP`
- [x] 4.2 Generate load against the app through Traefik (e.g. reuse `load-tests/check-rate-limit.js` with `BASE_URL=http://localhost`) and confirm the Grafana dashboard's total and per-instance panels reflect it — verified by running the dashboard's exact PromQL directly: 3 instances at ~33% each, summing to ~100%
- [x] 4.3 Scale to a different instance count (`--scale app=5`, then back down) and confirm Prometheus's target list and the dashboard update without any config changes — verified both directions (2→5 and 5→2)
- [x] 4.4 Confirm the dashboard requires no manual setup: a fresh `docker compose up` (no prior Grafana state) already has the datasource and dashboard present

## 5. Documentation

- [x] 5.1 Add a "Metrics dashboard" section to `README.md`: how to open Grafana (`http://localhost:3000`, default `admin`/`admin`), what the dashboard shows, and that Prometheus itself is browsable at `http://localhost:9090`
