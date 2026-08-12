package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Mono;

public final class DeleteResourceUseCase {

    private final ResourceRepositoryPort resourceRepositoryPort;

    public DeleteResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        this.resourceRepositoryPort = resourceRepositoryPort;
    }

    public Mono<Boolean> delete(TenantId tenantId, ResourceId resourceId) {
        return resourceRepositoryPort.findById(resourceId)
                .filter(resource -> resource.tenantId().equals(tenantId))
                .flatMap(resource -> {
                    resource.disable();
                    return resourceRepositoryPort.save(resource).thenReturn(true);
                })
                .defaultIfEmpty(false);
    }
}
