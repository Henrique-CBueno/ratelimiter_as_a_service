package com.ratelimitservice.rls.adapter.rest.resource;

import com.ratelimitservice.rls.adapter.rest.auth.AuthenticatedTenant;
import com.ratelimitservice.rls.adapter.rest.error.ResourceNotFoundException;
import com.ratelimitservice.rls.application.resource.CreateResourceUseCase;
import com.ratelimitservice.rls.application.resource.DeleteResourceUseCase;
import com.ratelimitservice.rls.application.resource.GetResourceUseCase;
import com.ratelimitservice.rls.application.resource.ListResourcesUseCase;
import com.ratelimitservice.rls.application.resource.UpdateResourceUseCase;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final CreateResourceUseCase createResourceUseCase;
    private final ListResourcesUseCase listResourcesUseCase;
    private final GetResourceUseCase getResourceUseCase;
    private final UpdateResourceUseCase updateResourceUseCase;
    private final DeleteResourceUseCase deleteResourceUseCase;

    public ResourceController(CreateResourceUseCase createResourceUseCase, ListResourcesUseCase listResourcesUseCase,
                               GetResourceUseCase getResourceUseCase, UpdateResourceUseCase updateResourceUseCase,
                               DeleteResourceUseCase deleteResourceUseCase) {
        this.createResourceUseCase = createResourceUseCase;
        this.listResourcesUseCase = listResourcesUseCase;
        this.getResourceUseCase = getResourceUseCase;
        this.updateResourceUseCase = updateResourceUseCase;
        this.deleteResourceUseCase = deleteResourceUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResourceResponse> create(ServerWebExchange exchange, @RequestBody CreateResourceRequest request) {
        Tenant tenant = authenticatedTenant(exchange);
        Quota quota = toQuota(request.limit(), request.windowSeconds(), request.burstCapacity());
        return createResourceUseCase
                .create(tenant.id(), request.resourceKey(), request.strategyType(), quota, request.fallbackPolicy())
                .map(ResourceResponse::from);
    }

    @GetMapping
    public Flux<ResourceResponse> list(ServerWebExchange exchange) {
        Tenant tenant = authenticatedTenant(exchange);
        return listResourcesUseCase.list(tenant.id()).map(ResourceResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<ResourceResponse> get(ServerWebExchange exchange, @PathVariable UUID id) {
        Tenant tenant = authenticatedTenant(exchange);
        return getResourceUseCase.get(tenant.id(), new ResourceId(id))
                .map(ResourceResponse::from)
                .switchIfEmpty(Mono.error(ResourceNotFoundException.forId(id)));
    }

    @PutMapping("/{id}")
    public Mono<ResourceResponse> update(ServerWebExchange exchange, @PathVariable UUID id,
                                          @RequestBody UpdateResourceRequest request) {
        Tenant tenant = authenticatedTenant(exchange);
        Quota quota = toQuota(request.limit(), request.windowSeconds(), request.burstCapacity());
        return updateResourceUseCase.update(tenant.id(), new ResourceId(id), request.strategyType(), quota)
                .map(ResourceResponse::from)
                .switchIfEmpty(Mono.error(ResourceNotFoundException.forId(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(ServerWebExchange exchange, @PathVariable UUID id) {
        Tenant tenant = authenticatedTenant(exchange);
        return deleteResourceUseCase.delete(tenant.id(), new ResourceId(id))
                .flatMap(deleted -> Boolean.TRUE.equals(deleted) ? Mono.empty() : Mono.error(ResourceNotFoundException.forId(id)));
    }

    private Tenant authenticatedTenant(ServerWebExchange exchange) {
        return exchange.getAttribute(AuthenticatedTenant.ATTRIBUTE);
    }

    private Quota toQuota(int limit, int windowSeconds, Integer burstCapacity) {
        Duration window = Duration.ofSeconds(windowSeconds);
        return burstCapacity != null ? Quota.withBurst(limit, window, burstCapacity) : Quota.of(limit, window);
    }
}
