## Context

`docker-compose.yml` today defines `redis`, `postgres`, and two hand-named services `app1`/`app2`, each `build: .` with a static host port mapping (`8081:8080`, `8082:8080`). This exists specifically to demonstrate that rate-limit state is coordinated through shared Redis/PostgreSQL rather than instance memory (the `containerized-deployment` spec's "multiple instances" requirement). Sessions for the `/app/**` web UI are already externalized to Redis (`spring.session.store-type: redis` in `rls-bootstrap`, `spring-session-data-redis` on the classpath), and Actuator already exposes `/actuator/health` and `/actuator/prometheus`. The runtime image (`eclipse-temurin:21-jre`, Debian-based) has no `curl`/`wget` installed.

## Goals / Non-Goals

**Goals:**
- Single HTTP entrypoint for the application, instead of clients needing to know per-instance ports.
- Support scaling to more than two instances without hand-editing `docker-compose.yml` (`docker compose up --scale app=N`).
- Automatic removal of unhealthy instances from rotation.

**Non-Goals:**
- No TLS/HTTPS termination in this change (HTTP only, matching the project's current dev/demo posture).
- No production-grade Traefik hardening (dashboard auth, ACLs) — this is a local/demo topology, same posture as the already-unauthenticated Actuator endpoints.
- No Compose-level (`healthcheck:`) health checks on `app` — Traefik's own health check covers instance removal from routing; adding a Docker-level healthcheck would require installing `curl`/`wget` into the runtime image for no additional benefit here.
- No changes to Prometheus scraping strategy — no real Prometheus server is deployed by this project; scraping through a round-robin load balancer would be unreliable per-series and is explicitly out of scope.

## Decisions

1. **Replace `app1`/`app2` with a single scalable `app` service, with no `ports:` mapping.** Compose cannot bind the same host port twice, which is exactly why the old topology needed two separately-named, separately-ported services. Removing the host port mapping entirely is what makes `docker compose up --scale app=N` possible: replicas only need distinct addresses on the internal Compose network, which Compose already guarantees.
2. **Traefik v3.x, Docker provider, label-based discovery.** Traefik watches the Docker socket (mounted read-only) and automatically picks up every container carrying `traefik.enable=true`, merging same-service-labeled containers into one dynamic backend — no static router config to update when scaling. Labels on `app`:
   - `traefik.enable=true`
   - `traefik.http.routers.app.rule=PathPrefix(`/`)`
   - `traefik.http.routers.app.entrypoints=web`
   - `traefik.http.services.app.loadbalancer.server.port=8080`
   - `traefik.http.services.app.loadbalancer.healthcheck.path=/actuator/health`
   - `traefik.http.services.app.loadbalancer.healthcheck.interval=5s`
   - `traefik.http.services.app.loadbalancer.healthcheck.timeout=3s`
3. **Round-robin, no sticky sessions.** Traefik's Docker-provider load balancer implements weighted round robin by default (no alternative algorithm to select). Deliberately not setting `loadbalancer.sticky.cookie`: since `WebSession` state (auth + CSRF for `/app/**`) is already stored in Redis, any instance can serve any session, and running without stickiness is what actually exercises that property under real traffic rather than assuming it.
4. **Traefik-native health checks, not Compose-level.** Traefik polls each backend's `/actuator/health` itself, from the `traefik` container — no tooling needed inside the `app` image. This is preferred over adding a `healthcheck:` block to the `app` Compose service, which would require installing `curl`/`wget` into the JRE-only runtime image for a check Traefik already performs.
5. **Traefik dashboard exposed on `:8080`, `--api.insecure=true`.** Acceptable for this project's local/dev-only posture (mirrors the already-accepted unauthenticated Actuator endpoints); documented as a risk, not hardened, since there's no production deployment target for this project today.

## Risks / Trade-offs

- [Risk] Traefik dashboard has no authentication (`--api.insecure=true`) → Mitigation: local/dev-only, documented in `README.md`; not intended for any shared/production environment.
- [Risk] Docker socket mounted into the `traefik` container grants it visibility into every container on the host, not just this project's → Mitigation: mounted read-only (`:ro`); accepted as a standard, well-understood Traefik-on-Docker trade-off for local development.
- [Risk] `/actuator/prometheus` scraped through the Traefik entrypoint would return a different instance's metrics on each scrape (round robin), breaking time-series continuity → Mitigation: out of scope — no Prometheus server is deployed by this project; if one is added later, it must scrape each container's internal Compose-network address directly, not through Traefik.
- [Risk] **BREAKING**: any tooling or documentation referencing `localhost:8081`/`localhost:8082` directly stops working → Mitigation: `README.md` updated in this change; the `add-load-testing` change's scripts already target a configurable `BASE_URL`, so they only need `BASE_URL=http://localhost` after this change lands, no script edits required.

## Migration Plan

1. Update `docker-compose.yml` (single `app` service + `traefik` service).
2. Update `README.md`'s Docker Compose section.
3. `docker compose down` (old topology) → `docker compose up --scale app=2` (or higher) to verify the new topology end-to-end, including that two-instance shared-state behavior still holds through the new entrypoint.
4. No database/Redis migration involved — purely a Compose topology and routing change.

Rollback: revert `docker-compose.yml` and `README.md` to the prior commit; no persistent state is affected by this change.
