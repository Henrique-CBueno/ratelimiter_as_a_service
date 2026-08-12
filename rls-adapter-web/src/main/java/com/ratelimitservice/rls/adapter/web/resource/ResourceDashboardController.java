package com.ratelimitservice.rls.adapter.web.resource;

import com.ratelimitservice.rls.adapter.web.auth.TenantPrincipal;
import com.ratelimitservice.rls.application.resource.CreateResourceUseCase;
import com.ratelimitservice.rls.application.resource.DeleteResourceUseCase;
import com.ratelimitservice.rls.application.resource.GetResourceUseCase;
import com.ratelimitservice.rls.application.resource.ListResourcesUseCase;
import com.ratelimitservice.rls.application.resource.UpdateResourceUseCase;
import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@org.springframework.stereotype.Controller
public class ResourceDashboardController {

    private final CreateResourceUseCase createResourceUseCase;
    private final ListResourcesUseCase listResourcesUseCase;
    private final GetResourceUseCase getResourceUseCase;
    private final UpdateResourceUseCase updateResourceUseCase;
    private final DeleteResourceUseCase deleteResourceUseCase;

    public ResourceDashboardController(CreateResourceUseCase createResourceUseCase, ListResourcesUseCase listResourcesUseCase,
                                        GetResourceUseCase getResourceUseCase, UpdateResourceUseCase updateResourceUseCase,
                                        DeleteResourceUseCase deleteResourceUseCase) {
        this.createResourceUseCase = createResourceUseCase;
        this.listResourcesUseCase = listResourcesUseCase;
        this.getResourceUseCase = getResourceUseCase;
        this.updateResourceUseCase = updateResourceUseCase;
        this.deleteResourceUseCase = deleteResourceUseCase;
    }

    @GetMapping("/app/resources")
    public Mono<Rendering> list(@AuthenticationPrincipal TenantPrincipal principal) {
        return listResourcesUseCase.list(principal.tenantId())
                .collectList()
                .map(resources -> Rendering.view("resources").modelAttribute("resources", resources).build());
    }

    @GetMapping("/app/resources/new")
    public Rendering newForm() {
        return Rendering.view("resource-form")
                .modelAttribute("mode", "create")
                .modelAttribute("strategies", StrategyType.values())
                .build();
    }

    @PostMapping("/app/resources")
    public Mono<Rendering> create(@AuthenticationPrincipal TenantPrincipal principal, @ModelAttribute CreateResourceForm form) {
        Quota quota = toQuota(form.getLimit(), form.getWindowSeconds(), form.getBurstCapacity());
        return createResourceUseCase.create(principal.tenantId(), form.getResourceKey(), form.getStrategyType(), quota, null)
                .map(resource -> Rendering.redirectTo("/app/resources").build())
                .onErrorResume(DuplicateResourceKeyException.class, ex -> Mono.just(Rendering.view("resource-form")
                        .modelAttribute("mode", "create")
                        .modelAttribute("strategies", StrategyType.values())
                        .modelAttribute("error", ex.getMessage())
                        .modelAttribute("resourceKey", form.getResourceKey())
                        .modelAttribute("strategyType", form.getStrategyType().name())
                        .modelAttribute("limit", form.getLimit())
                        .modelAttribute("windowSeconds", form.getWindowSeconds())
                        .modelAttribute("burstCapacity", form.getBurstCapacity())
                        .status(HttpStatus.CONFLICT)
                        .build()));
    }

    @GetMapping("/app/resources/{id}/edit")
    public Mono<Rendering> editForm(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id) {
        return getResourceUseCase.get(principal.tenantId(), new ResourceId(id))
                .map(resource -> Rendering.view("resource-form")
                        .modelAttribute("mode", "edit")
                        .modelAttribute("strategies", StrategyType.values())
                        .modelAttribute("resourceId", id)
                        .modelAttribute("resourceKey", resource.resourceKey())
                        .modelAttribute("strategyType", resource.strategyType().name())
                        .modelAttribute("limit", resource.quota().limit())
                        .modelAttribute("windowSeconds", resource.quota().window().toSeconds())
                        .modelAttribute("burstCapacity", resource.quota().burstCapacity())
                        .build())
                .switchIfEmpty(Mono.just(notFound()));
    }

    @PostMapping("/app/resources/{id}")
    public Mono<Rendering> update(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id,
                                   @ModelAttribute EditResourceForm form) {
        Quota quota = toQuota(form.getLimit(), form.getWindowSeconds(), form.getBurstCapacity());
        return updateResourceUseCase.update(principal.tenantId(), new ResourceId(id), form.getStrategyType(), quota)
                .map(resource -> Rendering.redirectTo("/app/resources").build())
                .switchIfEmpty(Mono.just(notFound()));
    }

    @PostMapping("/app/resources/{id}/delete")
    public Mono<Rendering> delete(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id) {
        return deleteResourceUseCase.delete(principal.tenantId(), new ResourceId(id))
                .map(deleted -> deleted ? Rendering.redirectTo("/app/resources").build() : notFound());
    }

    private Rendering notFound() {
        return Rendering.view("not-found").status(HttpStatus.NOT_FOUND).build();
    }

    private Quota toQuota(int limit, int windowSeconds, Integer burstCapacity) {
        Duration window = Duration.ofSeconds(windowSeconds);
        return burstCapacity != null ? Quota.withBurst(limit, window, burstCapacity) : Quota.of(limit, window);
    }
}
