package com.ratelimitservice.rls.domain.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * Plain data carrier for reconstituting an {@link ApiToken}'s historical state (id, hash, prefix,
 * created/revoked timestamps) from storage. Passed to {@link Tenant#reconstitute}, which is the
 * only place an {@link ApiToken} can be rebuilt from this shape.
 */
public record ApiTokenData(UUID id, String tokenHash, String tokenPrefix, Instant createdAt, Instant revokedAt) {
}
