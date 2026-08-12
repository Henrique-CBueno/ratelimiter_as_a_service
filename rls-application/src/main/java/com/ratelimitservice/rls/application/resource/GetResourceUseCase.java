package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Mono;

public final class GetResourceUseCase {

    private final ResourceRepositoryPort resourceRepositoryPort;

    public GetResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        this.resourceRepositoryPort = resourceRepositoryPort;
    }

    public Mono<RateLimitResource> get(TenantId tenantId, ResourceId resourceId) {
        return resourceRepositoryPort.findById(resourceId)
                .filter(resource -> resource.tenantId().equals(tenantId));
    }
}
