package com.ratelimitservice.rls.application.resource.port;

import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResourceRepositoryPort {

    Mono<RateLimitResource> save(RateLimitResource resource);

    Mono<RateLimitResource> findById(ResourceId id);

    Mono<RateLimitResource> findByTenantAndKey(TenantId tenantId, String resourceKey);

    Flux<RateLimitResource> findAllByTenant(TenantId tenantId);
}
