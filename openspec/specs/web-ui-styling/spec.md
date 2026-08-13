# web-ui-styling Specification

## Purpose
TBD - created by archiving change improve-ui-styling. Update Purpose after archive.
## Requirements
### Requirement: All web pages load a shared compiled stylesheet
The system SHALL serve a single compiled CSS stylesheet as a static asset, linked from every Thymeleaf page via a shared fragment.

#### Scenario: A page includes the shared stylesheet
- **WHEN** any of the application's Thymeleaf pages (login, register, register-success, resources, resource-form, settings, not-found) is rendered
- **THEN** the response's `<head>` includes a `<link>` to the compiled stylesheet at `/css/app.css`, and requesting that URL returns the compiled CSS successfully

### Requirement: The stylesheet is compiled automatically during the build
The system SHALL regenerate the compiled stylesheet automatically as part of the Maven build, with no manual compilation step and no Node/npm dependency.

#### Scenario: Building the project produces an up-to-date stylesheet
- **WHEN** `mvn package` is run on `rls-adapter-web` (directly or as part of the full reactor build)
- **THEN** the build downloads (if not already present) and invokes the Tailwind standalone CLI binary, producing a compiled stylesheet reflecting the current Tailwind classes used in the templates, without requiring Node or npm to be installed

#### Scenario: Building the Docker image produces a working stylesheet
- **WHEN** `docker build` is run against the repository root
- **THEN** the resulting image serves the compiled stylesheet at `/css/app.css` without any manual build step outside the Docker build process

