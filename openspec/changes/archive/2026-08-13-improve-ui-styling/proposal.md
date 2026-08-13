## Why

The Thymeleaf web UI (login, registration, resource dashboard, resource form, settings, not-found, register-success) is fully functional but entirely unstyled — plain browser-default HTML with no CSS anywhere in the project. It works for verifying behavior but isn't presentable.

## What Changes

- Add Tailwind CSS styling to all 7 existing Thymeleaf templates under `rls-adapter-web/src/main/resources/templates/`.
- Compile Tailwind via its standalone CLI binary (no Node/npm, no `package.json`), wired into the `rls-adapter-web` Maven build so `mvn package`/`docker build` produce the compiled stylesheet automatically.
- Add a new shared `head` fragment to `fragments/layout.html` that links the compiled stylesheet, consumed by every template.
- Style the shared nav, tables (resources dashboard), forms (login/register/resource-form/settings), buttons, and error messaging consistently across pages.

## Capabilities

### New Capabilities
- `web-ui-styling`: a compiled, versioned Tailwind stylesheet served as a static asset and applied consistently across all Thymeleaf pages, built automatically as part of the Maven build with no Node/npm dependency.

### Modified Capabilities
(none — purely additive visual/asset changes; no existing behavioral requirement in `web-authentication`, `web-resource-dashboard`, or `web-tenant-onboarding` changes)

## Impact

- `rls-adapter-web/pom.xml`: new build plugin wiring (downloads and invokes the Tailwind standalone CLI during `generate-resources`).
- `rls-adapter-web/src/main/resources/tailwind/input.css`: new Tailwind v4 CSS-first entry point.
- `rls-adapter-web/src/main/resources/templates/fragments/layout.html` and all 7 page templates: new shared `head` fragment + Tailwind utility classes.
- `Dockerfile`: build stage needs `curl` added to download the Tailwind binary during `docker build`.
- `.gitignore`: exclude the downloaded platform-specific Tailwind binary.
