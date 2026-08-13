## 1. Tooling setup

- [x] 1.1 Add `rls-adapter-web/bin/` to root `.gitignore`
- [x] 1.2 Write download scripts for the Tailwind standalone CLI binary: a shell script (Linux, used in `Dockerfile`) and a PowerShell script (Windows, for local dev), both idempotent (skip if the binary already exists)
- [x] 1.3 Add `exec-maven-plugin` to `rls-adapter-web/pom.xml` with two `generate-resources` executions: download the binary, then compile `tailwind/input.css` into `target/classes/static/css/app.css`

## 2. Tailwind input

- [x] 2.1 Create `rls-adapter-web/src/main/resources/tailwind/input.css` with `@import "tailwindcss";` and `@source` directives covering `templates/**/*.html`
- [x] 2.2 Run a local build and confirm `target/classes/static/css/app.css` is generated and non-empty

## 3. Shared layout fragment

- [x] 3.1 Add a `head` fragment to `fragments/layout.html` (title param + `<link rel="stylesheet" th:href="@{/css/app.css}"/>`)
- [x] 3.2 Style the shared `nav` fragment (and `error` fragment) with Tailwind utility classes

## 4. Page-by-page styling

- [x] 4.1 Update `login.html` to consume the `head` fragment and style the login form
- [x] 4.2 Update `register.html` to consume the `head` fragment and style the registration form
- [x] 4.3 Update `register-success.html` to consume the `head` fragment and style the one-time token display
- [x] 4.4 Update `resources.html` to consume the `head` fragment and style the resources table + actions
- [x] 4.5 Update `resource-form.html` to consume the `head` fragment and style the create/edit form
- [x] 4.6 Update `settings.html` to consume the `head` fragment and style the token/rotate view
- [x] 4.7 Update `not-found.html` to consume the `head` fragment and style the not-found page

## 5. Docker build

- [x] 5.1 Add `curl`/`ca-certificates` to the `Dockerfile`'s Maven build stage
- [x] 5.2 Run `docker build` end-to-end and confirm `/css/app.css` is served correctly from the resulting image

## 6. Verification

- [x] 6.1 Load pages via the running container and confirm consistent styling (nav, forms, table, buttons, error messages) — verified via HTTP against `docker compose up` (no GUI browser available in this environment)
- [x] 6.2 Confirm existing web E2E tests (`WebAuthenticationE2EIT`, etc.) still pass — required updating one markup-coupled assertion in `WebOnboardingAndDashboardE2EIT` (exact `<td>false</td>` match) to tolerate the added `class` attribute; no behavioral change
