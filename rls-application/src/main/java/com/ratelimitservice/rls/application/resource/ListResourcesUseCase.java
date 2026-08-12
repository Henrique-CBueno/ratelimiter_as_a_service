package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Flux;

public final class ListResourcesUseCase {

    private final ResourceRepositoryPort resourceRepositoryPort;

    public ListResourcesUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        this.resourceRepositoryPort = resourceRepositoryPort;
    }

    public Flux<RateLimitResource> list(TenantId tenantId) {
        return resourceRepositoryPort.findAllByTenant(tenantId);
    }
}
