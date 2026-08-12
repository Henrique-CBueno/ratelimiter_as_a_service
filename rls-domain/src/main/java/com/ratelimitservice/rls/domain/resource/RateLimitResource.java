package com.ratelimitservice.rls.domain.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;

import java.util.Objects;

public final class RateLimitResource {

    private final ResourceId id;
    private final TenantId tenantId;
    private final String resourceKey;
    private final FallbackPolicy fallbackPolicy;
    private StrategyType strategyType;
    private Quota quota;
    private boolean enabled;

    private RateLimitResource(ResourceId id, TenantId tenantId, String resourceKey, StrategyType strategyType,
                               Quota quota, FallbackPolicy fallbackPolicy, boolean enabled) {
        this.id = id;
        this.tenantId = tenantId;
        this.resourceKey = resourceKey;
        this.strategyType = strategyType;
        this.quota = quota;
        this.fallbackPolicy = fallbackPolicy;
        this.enabled = enabled;
    }

    public static RateLimitResource create(TenantId tenantId, String resourceKey, StrategyType strategyType, Quota quota) {
        return create(tenantId, resourceKey, strategyType, quota, null);
    }

    public static RateLimitResource create(TenantId tenantId, String resourceKey, StrategyType strategyType,
                                             Quota quota, FallbackPolicy fallbackPolicy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireNonBlank(resourceKey, "resourceKey");
        Objects.requireNonNull(strategyType, "strategyType must not be null");
        Objects.requireNonNull(quota, "quota must not be null");
        return new RateLimitResource(ResourceId.generate(), tenantId, resourceKey, strategyType, quota, fallbackPolicy, true);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    public FallbackPolicy resolveFallbackPolicy(Tenant tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return fallbackPolicy != null ? fallbackPolicy : tenant.defaultFallbackPolicy();
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void reconfigure(StrategyType newStrategyType, Quota newQuota) {
        Objects.requireNonNull(newStrategyType, "newStrategyType must not be null");
        Objects.requireNonNull(newQuota, "newQuota must not be null");
        this.strategyType = newStrategyType;
        this.quota = newQuota;
    }

    public ResourceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String resourceKey() {
        return resourceKey;
    }

    public StrategyType strategyType() {
        return strategyType;
    }

    public Quota quota() {
        return quota;
    }

    public FallbackPolicy fallbackPolicy() {
        return fallbackPolicy;
    }

    public boolean enabled() {
        return enabled;
    }
}
