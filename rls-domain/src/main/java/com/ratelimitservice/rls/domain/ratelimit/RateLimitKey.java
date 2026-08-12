package com.ratelimitservice.rls.domain.ratelimit;

import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;

import java.util.Objects;

public record RateLimitKey(TenantId tenantId, ResourceId resourceId, ClientIp clientIp, StrategyType strategyType) {

    public RateLimitKey {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(clientIp, "clientIp must not be null");
        Objects.requireNonNull(strategyType, "strategyType must not be null");
    }
}
