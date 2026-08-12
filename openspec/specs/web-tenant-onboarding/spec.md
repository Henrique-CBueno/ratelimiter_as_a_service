# web-tenant-onboarding Specification

## Purpose
TBD - created by syncing change thymeleaf-frontend. Update Purpose after archive.

## Requirements

### Requirement: Public registration page
The system SHALL expose `GET /app/register`, unauthenticated, rendering a form for name, email,
and password.

#### Scenario: Registration form is publicly reachable
- **WHEN** `GET /app/register` is requested without a session
- **THEN** the response is `200 OK` with a rendered registration form

### Requirement: Registration submission creates a tenant and reveals its API token once
The system SHALL expose `POST /app/register`, unauthenticated, accepting the registration form,
creating a new tenant via the same use case the REST API uses, and showing the newly issued API
token exactly once.

#### Scenario: Successful registration shows the token once
- **WHEN** `POST /app/register` is submitted with a valid name, a well-formed unused email, and a
  non-blank password
- **THEN** the response renders a confirmation page containing the new tenant's API token, and that
  token is never shown again on any subsequent page

#### Scenario: Registration rejects a duplicate email
- **WHEN** `POST /app/register` is submitted with an email already used by a previously registered
  tenant
- **THEN** the registration form is re-rendered with an inline error indicating the email is
  already in use, the submitted password is not retained in the re-rendered form, and no new
  tenant is created

### Requirement: Registration is CSRF-protected
The system SHALL reject `POST /app/register` submissions that lack a valid CSRF token.

#### Scenario: Submission without a valid CSRF token is rejected
- **WHEN** `POST /app/register` is submitted without the CSRF token issued for that form
- **THEN** the request is rejected and no tenant is created
