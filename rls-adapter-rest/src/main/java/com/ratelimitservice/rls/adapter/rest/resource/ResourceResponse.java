package com.ratelimitservice.rls.adapter.rest.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;

import java.util.UUID;

public record ResourceResponse(UUID id, String resourceKey, StrategyType strategyType, int limit,
                                int windowSeconds, Integer burstCapacity, FallbackPolicy fallbackPolicy,
                                boolean enabled) {

    public static ResourceResponse from(RateLimitResource resource) {
        Quota quota = resource.quota();
        return new ResourceResponse(
                resource.id().value(),
                resource.resourceKey(),
                resource.strategyType(),
                quota.limit(),
                (int) quota.window().toSeconds(),
                quota.burstCapacity(),
                resource.fallbackPolicy(),
                resource.enabled());
    }
}
