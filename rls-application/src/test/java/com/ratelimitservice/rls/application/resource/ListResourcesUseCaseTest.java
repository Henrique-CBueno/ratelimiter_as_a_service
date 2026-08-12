package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ListResourcesUseCaseTest {

    private final InMemoryResourceRepositoryPort repository = new InMemoryResourceRepositoryPort();
    private final ListResourcesUseCase useCase = new ListResourcesUseCase(repository);

    @Test
    void listsOnlyTheGivenTenantsResources() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        repository.save(RateLimitResource.create(tenantA, "/a1", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
        repository.save(RateLimitResource.create(tenantA, "/a2", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
        repository.save(RateLimitResource.create(tenantB, "/b1", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();

        var resources = useCase.list(tenantA).collectList().block();

        assertThat(resources).hasSize(2);
        assertThat(resources).allMatch(r -> r.tenantId().equals(tenantA));
    }
}
