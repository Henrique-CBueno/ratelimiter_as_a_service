## Why

`docker-compose.yml` already runs two fixed application instances (`app1` on host port 8081, `app2` on host port 8082) sharing the same Redis/PostgreSQL, to demonstrate horizontal scalability. But there is no single entrypoint: a client has to know and pick a port, the topology can't grow past two instances without hand-editing the compose file, and nothing automatically stops routing to an unhealthy instance. A load balancer turns "two instances that happen to exist" into an actual horizontally-scalable deployment.

## What Changes

- **BREAKING**: Replace the fixed `app1`/`app2` services in `docker-compose.yml` with a single scalable `app` service (no static host port mapping), started with `docker compose up --scale app=N`.
- Add a `traefik` service (Traefik v3.x) as the single HTTP entrypoint on port 80, using Traefik's Docker provider for automatic service discovery (label-based, no static router config to maintain per instance).
- Route to `app` instances via round-robin (Traefik's default and only algorithm for its Docker provider), with Traefik-native health checks against each instance's `/actuator/health` — no sticky sessions, since Spring Session is already Redis-backed and any instance can serve any session.
- Update `README.md`'s Docker Compose usage section: access the app via `http://localhost/...` (not per-instance ports), how to scale (`--scale app=N`), and the Traefik dashboard location.
- Update the `containerized-deployment` capability's "multiple instances" requirement to reflect verification through the Traefik-fronted entrypoint plus `--scale`, replacing the old two-named-ports verification story.

## Capabilities

### New Capabilities
- `load-balancing`: Traefik-based reverse proxy routing requests across N healthy application instances via Docker-provider service discovery, round-robin distribution, and health-check-based instance removal.

### Modified Capabilities
- `containerized-deployment`: the "Docker Compose runs multiple stateless application instances against shared state" requirement changes from two fixed named/ported services to a single scalable service fronted by Traefik.

## Impact

- `docker-compose.yml`: `app1`/`app2` replaced by `app` (scalable, no host port) + new `traefik` service.
- `README.md`: Docker Compose usage section rewritten for the new entrypoint/scaling model.
- `openspec/specs/containerized-deployment/spec.md`: one requirement's scenario updated (delta spec in this change; synced to the main spec via `/opsx:sync` or archive).
- No application code changes — Actuator's `/actuator/health` (already exposed) is reused as-is for Traefik's health checks.
