## MODIFIED Requirements

### Requirement: Credential hashing never stores or returns plaintext
The system SHALL provide a `SecretHasherPort` (BCrypt-backed) for tenant passwords, turning a raw
secret into a verifiable, salted hash without the hash allowing recovery of the original secret,
and a matching verification operation. API token fingerprints are computed separately (see the
`resilient-evaluation-fallback`/`tenant-onboarding-api` capabilities' use of a deterministic SHA-256
fingerprint) since `TenantRepositoryPort.findByActiveTokenHash` must look up a tenant *by* the
fingerprint value — a salted hash such as BCrypt's produces a different output on every call for the
same input, making that lookup impossible; a high-entropy random token does not need a salted,
slow hash to resist brute force the way a human-chosen password does, so a plain deterministic
digest is sufficient and is what makes the lookup possible at all.

#### Scenario: A raw secret's hash verifies successfully against that same secret
- **WHEN** a raw password is hashed via `SecretHasherPort.hash(rawSecret)` and then checked via
  `SecretHasherPort.matches(rawSecret, hash)`
- **THEN** the match succeeds

#### Scenario: A hash does not verify against a different secret
- **WHEN** `SecretHasherPort.matches(differentRawSecret, hash)` is called with a secret other
  than the one originally hashed
- **THEN** the match fails

#### Scenario: API token fingerprinting is deterministic, unlike password hashing
- **WHEN** the same raw API token is fingerprinted twice
- **THEN** both fingerprints are identical, so `TenantRepositoryPort.findByActiveTokenHash` can look
  up the owning tenant by that value
