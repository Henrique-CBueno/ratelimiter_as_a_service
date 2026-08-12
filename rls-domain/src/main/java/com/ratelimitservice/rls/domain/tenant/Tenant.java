package com.ratelimitservice.rls.domain.tenant;

import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Tenant {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantId id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final FallbackPolicy defaultFallbackPolicy;
    private final List<ApiToken> apiTokens = new ArrayList<>();
    private TenantStatus status;

    private Tenant(TenantId id, String name, String email, String passwordHash,
                    TenantStatus status, FallbackPolicy defaultFallbackPolicy) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.defaultFallbackPolicy = defaultFallbackPolicy;
    }

    public static Tenant register(String name, String email, String passwordHash, FallbackPolicy defaultFallbackPolicy) {
        requireNonBlank(name, "name");
        requireNonBlank(email, "email");
        requireNonBlank(passwordHash, "passwordHash");
        Objects.requireNonNull(defaultFallbackPolicy, "defaultFallbackPolicy must not be null");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + email);
        }
        return new Tenant(TenantId.generate(), name, email, passwordHash, TenantStatus.ACTIVE, defaultFallbackPolicy);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /**
     * Rebuilds a {@code Tenant} exactly as previously persisted (existing id, status, and token
     * history) — for use by repository adapters only. Unlike {@link #register}, this does not
     * generate a new id or default to {@code ACTIVE}/an empty token list.
     */
    public static Tenant reconstitute(TenantId id, String name, String email, String passwordHash,
                                       TenantStatus status, FallbackPolicy defaultFallbackPolicy,
                                       List<ApiTokenData> tokenData) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(defaultFallbackPolicy, "defaultFallbackPolicy must not be null");
        Objects.requireNonNull(tokenData, "tokenData must not be null");

        Tenant tenant = new Tenant(id, name, email, passwordHash, status, defaultFallbackPolicy);
        for (ApiTokenData data : tokenData) {
            tenant.apiTokens.add(ApiToken.restore(data.id(), data.tokenHash(), data.tokenPrefix(),
                    data.createdAt(), data.revokedAt()));
        }
        return tenant;
    }

    public void suspend() {
        if (status != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Only an active tenant can be suspended");
        }
        this.status = TenantStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != TenantStatus.SUSPENDED) {
            throw new IllegalStateException("Only a suspended tenant can be reactivated");
        }
        this.status = TenantStatus.ACTIVE;
    }

    public ApiToken issueApiToken(String tokenHash, String tokenPrefix, Instant now) {
        ApiToken token = ApiToken.issue(tokenHash, tokenPrefix, now);
        apiTokens.add(token);
        return token;
    }

    public ApiToken rotateApiToken(String newTokenHash, String newTokenPrefix, Instant now) {
        findActiveToken().ifPresent(active -> active.revoke(now));
        return issueApiToken(newTokenHash, newTokenPrefix, now);
    }

    private Optional<ApiToken> findActiveToken() {
        return apiTokens.stream().filter(ApiToken::isActive).findFirst();
    }

    public TenantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public TenantStatus status() {
        return status;
    }

    public FallbackPolicy defaultFallbackPolicy() {
        return defaultFallbackPolicy;
    }

    public List<ApiToken> apiTokens() {
        return List.copyOf(apiTokens);
    }
}
