package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GetResourceUseCaseTest {

    private final InMemoryResourceRepositoryPort repository = new InMemoryResourceRepositoryPort();
    private final GetResourceUseCase useCase = new GetResourceUseCase(repository);

    @Test
    void returnsTheResourceWhenOwnedByTheGivenTenant() {
        TenantId tenantId = TenantId.generate();
        RateLimitResource resource = RateLimitResource.create(tenantId, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        var found = useCase.get(tenantId, resource.id()).block();

        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo(resource.id());
    }

    @Test
    void returnsEmptyWhenOwnedByADifferentTenant() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        RateLimitResource resource = RateLimitResource.create(owner, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        var found = useCase.get(other, resource.id()).block();

        assertThat(found).isNull();
    }

    @Test
    void returnsEmptyWhenTheResourceDoesNotExist() {
        var found = useCase.get(TenantId.generate(), ResourceId.generate()).block();

        assertThat(found).isNull();
    }
}
