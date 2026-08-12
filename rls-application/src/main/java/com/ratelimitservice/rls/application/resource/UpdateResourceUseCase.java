package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Mono;

public final class UpdateResourceUseCase {

    private final ResourceRepositoryPort resourceRepositoryPort;

    public UpdateResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        this.resourceRepositoryPort = resourceRepositoryPort;
    }

    public Mono<RateLimitResource> update(TenantId tenantId, ResourceId resourceId,
                                           StrategyType newStrategyType, Quota newQuota) {
        return resourceRepositoryPort.findById(resourceId)
                .filter(resource -> resource.tenantId().equals(tenantId))
                .flatMap(resource -> {
                    resource.reconfigure(newStrategyType, newQuota);
                    return resourceRepositoryPort.save(resource);
                });
    }
}
