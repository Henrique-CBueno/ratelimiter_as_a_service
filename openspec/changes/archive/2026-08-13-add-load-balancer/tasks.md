## 1. Docker Compose topology

- [x] 1.1 Remove `app1`/`app2` services from `docker-compose.yml`; add a single `app` service (same `build: .`, same `environment`/`depends_on` as before) with no `ports:` mapping
- [x] 1.2 Add `traefik` service (pinned `traefik:v3.x` image) with `--providers.docker=true`, `--providers.docker.exposedbydefault=false`, `--entrypoints.web.address=:80`, `--api.dashboard=true`, `--api.insecure=true`; publish ports `80:80` and `8080:8080`; mount `/var/run/docker.sock:/var/run/docker.sock:ro`; `depends_on: app`
- [x] 1.3 Add Traefik labels to `app`: `traefik.enable=true`, router rule (`PathPrefix(\`/\`)`), entrypoint `web`, `loadbalancer.server.port=8080`, and health-check labels (`healthcheck.path=/actuator/health`, interval, timeout)

## 2. Verification

- [x] 2.1 `docker compose up --scale app=2` — confirm both instances register with Traefik and requests to `http://localhost/actuator/health` succeed
- [x] 2.2 Send alternating requests for the same tenant/resource/client through `http://localhost/api/v1/ratelimit/check` exceeding the configured limit; confirm the combined allowed count across instances does not exceed the limit
- [x] 2.3 Stop one instance's health (e.g. temporarily block its `/actuator/health`) and confirm Traefik stops routing to it, then resumes once healthy again
- [x] 2.4 `docker compose up --scale app=4` — confirm Traefik picks up the additional instances without any config file change

## 3. Documentation

- [x] 3.1 Update `README.md`'s Docker Compose section: access via `http://localhost/...`, `--scale app=N` usage, Traefik dashboard at `http://localhost:8080`
- [x] 3.2 Note in `README.md` that individual instances are no longer reachable by a dedicated host port

## 4. Spec sync

- [ ] 4.1 After implementation, sync this change's `containerized-deployment` delta and the new `load-balancing` spec into `openspec/specs/` (via `/opsx:sync` or archive)
