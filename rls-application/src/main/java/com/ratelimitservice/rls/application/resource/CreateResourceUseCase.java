package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Mono;

public final class CreateResourceUseCase {

    private final ResourceRepositoryPort resourceRepositoryPort;

    public CreateResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        this.resourceRepositoryPort = resourceRepositoryPort;
    }

    public Mono<RateLimitResource> create(TenantId tenantId, String resourceKey, StrategyType strategyType,
                                           Quota quota, FallbackPolicy fallbackPolicy) {
        RateLimitResource resource = RateLimitResource.create(tenantId, resourceKey, strategyType, quota, fallbackPolicy);
        return resourceRepositoryPort.save(resource);
    }
}
