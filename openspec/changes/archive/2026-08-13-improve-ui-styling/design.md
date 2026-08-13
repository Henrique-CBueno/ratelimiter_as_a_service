## Context

`rls-adapter-web` owns every web/UI concern already (`SecurityConfig`, Thymeleaf controllers, templates). Its 7 templates each independently declare `<head><title>…</title></head>` with no shared `<head>` fragment; `fragments/layout.html` currently exposes only `nav` and `error` fragments. There is no `static/` resources folder anywhere in the project, and no Node/npm tooling exists in this otherwise 100%-Java/Maven repository. The `Dockerfile`'s build stage (`maven:3.9-eclipse-temurin-21`, Debian-based) currently has no `curl`; the runtime stage (`eclipse-temurin:21-jre`) is irrelevant here since compilation happens at build time, not runtime.

## Goals / Non-Goals

**Goals:**
- Consistent, reasonably polished visual styling across all 7 existing pages using Tailwind CSS.
- Zero new Node/npm dependency — stay within the project's existing all-Java/Maven toolchain philosophy.
- Stylesheet regenerates automatically on every `mvn package`/`docker build`, never manually maintained or hand-committed.

**Non-Goals:**
- No new pages or UI features — this change only styles what already exists.
- No component library/JS framework (Alpine.js, htmx, etc.) — Tailwind CSS only, no behavioral changes to templates.
- No dark mode / theming system — a single consistent light theme is sufficient for this change.

## Decisions

1. **Tailwind CLI, standalone binary** (not the Node/npm pipeline, not the Play CDN). The standalone binary needs no Node/npm install, keeping the repo's toolchain 100% Java + a small, easily-removable binary — closest fit for a project with zero existing JS tooling. Play CDN was rejected because Tailwind's own docs recommend against it outside quick demos (no class purging, larger payload); the full Node/npm pipeline was rejected because it would introduce an entire second package ecosystem (`package.json`, `node_modules`) into a repo that has deliberately stayed pure Java/Maven so far.
2. **Maven wiring via `exec-maven-plugin`** in `rls-adapter-web/pom.xml`, bound to the `generate-resources` phase, with two executions: (a) download the platform-appropriate Tailwind CLI binary into `rls-adapter-web/bin/` if not already present (idempotent — skipped on subsequent builds), (b) invoke that binary to compile `tailwind/input.css` into `target/classes/static/css/app.css`. `generate-resources` runs before `process-resources` copies `src/main/resources` into `target/classes`, so the generated CSS and the checked-in templates end up together correctly in the final jar.
3. **Output written to `target/classes/static/css/app.css`, not into `src/main/resources/static/`.** Avoids ever having a build-generated file living inside `src/`, so nothing needs to be gitignored inside the source tree — only `rls-adapter-web/bin/` (the downloaded binary) needs a `.gitignore` entry.
4. **Tailwind v4 CSS-first config**: `rls-adapter-web/src/main/resources/tailwind/input.css` contains `@import "tailwindcss";` plus `@source` directives pointing at `templates/**/*.html` for class scanning — no separate `tailwind.config.js`, consistent with "CSS-based config" and avoiding a JS config file in a Java project.
5. **Shared `head` Thymeleaf fragment**: add a `th:fragment="head(title)"` (or similar) to `fragments/layout.html` containing `<link rel="stylesheet" th:href="@{/css/app.css}"/>` plus the page `<title>`; each of the 7 templates' `<head>` is updated to consume it via `th:replace`, consistent with how the project already builds URLs with `th:href="@{...}"` elsewhere (e.g. `resources.html`).
6. **Static asset serving**: placing the compiled CSS on the classpath at `static/css/app.css` uses Spring Boot's default static-resource handling — served at `GET /css/app.css` with no additional `WebFluxConfigurer` needed, same mechanism as any other Spring Boot static asset.
7. **`Dockerfile` build-stage change**: add `curl`/`ca-certificates` to the Maven build stage so the Tailwind binary can be downloaded during `docker build` (the Linux binary is downloaded unconditionally in that stage, since the build stage's OS is fixed and known — no OS-detection needed there; OS-detection is only needed for local developer machines, e.g. this project's Windows dev environment).

## Risks / Trade-offs

- [Risk] Download script needs to branch on OS/arch for local development (Windows dev machine vs. Linux Docker build stage) → Mitigation: a small shell script (Linux/macOS, used in Docker) and PowerShell script (Windows, used locally) pair, both trivial (~15 lines), no new dependencies.
- [Risk] `docker build` now requires network access at build time to fetch the Tailwind binary (previously only Maven dependencies were fetched, already a network dependency) → Mitigation: acceptable, consistent with the existing Maven-dependency-fetching build; no new category of risk.
- [Risk] Forgetting to add `rls-adapter-web/bin/` to `.gitignore` could accidentally commit a multi-MB per-OS binary → Mitigation: add the `.gitignore` entry as an explicit first task, before the download script exists.
- [Risk] `add-load-balancer` and `add-load-testing` both rebuild the Docker image; if this change's `Dockerfile` edit (adding `curl`) isn't applied first, later `docker build` runs would fail on the missing Tailwind binary download step → Mitigation: no hard ordering is required since each change is independently applied, but note in this change's tasks that `docker build` must be re-verified after this change lands, regardless of which change was applied most recently.
