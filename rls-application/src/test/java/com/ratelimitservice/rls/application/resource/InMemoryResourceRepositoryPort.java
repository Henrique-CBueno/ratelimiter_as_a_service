package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryResourceRepositoryPort implements ResourceRepositoryPort {

    private final Map<ResourceId, RateLimitResource> byId = new LinkedHashMap<>();

    @Override
    public Mono<RateLimitResource> save(RateLimitResource resource) {
        boolean keyTakenByAnother = byId.values().stream()
                .anyMatch(existing -> !existing.id().equals(resource.id())
                        && existing.tenantId().equals(resource.tenantId())
                        && existing.resourceKey().equals(resource.resourceKey()));
        if (keyTakenByAnother) {
            return Mono.error(new DuplicateResourceKeyException(resource.resourceKey()));
        }
        byId.put(resource.id(), resource);
        return Mono.just(resource);
    }

    @Override
    public Mono<RateLimitResource> findById(ResourceId id) {
        RateLimitResource resource = byId.get(id);
        return resource != null ? Mono.just(resource) : Mono.empty();
    }

    @Override
    public Mono<RateLimitResource> findByTenantAndKey(TenantId tenantId, String resourceKey) {
        return byId.values().stream()
                .filter(r -> r.tenantId().equals(tenantId) && r.resourceKey().equals(resourceKey))
                .findFirst()
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }

    @Override
    public Flux<RateLimitResource> findAllByTenant(TenantId tenantId) {
        return Flux.fromStream(byId.values().stream().filter(r -> r.tenantId().equals(tenantId)));
    }
}
