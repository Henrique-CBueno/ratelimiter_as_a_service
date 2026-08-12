package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateResourceUseCaseTest {

    private final InMemoryResourceRepositoryPort repository = new InMemoryResourceRepositoryPort();
    private final UpdateResourceUseCase useCase = new UpdateResourceUseCase(repository);

    @Test
    void reconfiguresAResourceOwnedByTheGivenTenant() {
        TenantId tenantId = TenantId.generate();
        RateLimitResource resource = RateLimitResource.create(tenantId, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();
        Quota newQuota = Quota.withBurst(5, Duration.ofSeconds(30), 5);

        var updated = useCase.update(tenantId, resource.id(), StrategyType.TOKEN_BUCKET, newQuota).block();

        assertThat(updated).isNotNull();
        assertThat(updated.strategyType()).isEqualTo(StrategyType.TOKEN_BUCKET);
        assertThat(updated.quota()).isEqualTo(newQuota);
    }

    @Test
    void returnsEmptyAndLeavesUnchangedWhenOwnedByADifferentTenant() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        RateLimitResource resource = RateLimitResource.create(owner, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        var updated = useCase.update(other, resource.id(), StrategyType.TOKEN_BUCKET, Quota.withBurst(5, Duration.ofSeconds(30), 5)).block();

        assertThat(updated).isNull();
        assertThat(repository.findById(resource.id()).block().strategyType()).isEqualTo(StrategyType.FIXED_WINDOW);
    }
}
