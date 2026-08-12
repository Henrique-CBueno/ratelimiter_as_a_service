package com.ratelimitservice.rls.domain.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ApiToken {

    private final UUID id;
    private final String tokenHash;
    private final String tokenPrefix;
    private final Instant createdAt;
    private Instant revokedAt;

    private ApiToken(UUID id, String tokenHash, String tokenPrefix, Instant createdAt, Instant revokedAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    static ApiToken issue(String tokenHash, String tokenPrefix, Instant now) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(tokenPrefix, "tokenPrefix must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new ApiToken(UUID.randomUUID(), tokenHash, tokenPrefix, now, null);
    }

    static ApiToken restore(UUID id, String tokenHash, String tokenPrefix, Instant createdAt, Instant revokedAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(tokenPrefix, "tokenPrefix must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new ApiToken(id, tokenHash, tokenPrefix, createdAt, revokedAt);
    }

    void revoke(Instant now) {
        this.revokedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID id() {
        return id;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String tokenPrefix() {
        return tokenPrefix;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
