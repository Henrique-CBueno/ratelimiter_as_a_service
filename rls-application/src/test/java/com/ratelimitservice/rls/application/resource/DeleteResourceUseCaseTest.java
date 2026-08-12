package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteResourceUseCaseTest {

    private final InMemoryResourceRepositoryPort repository = new InMemoryResourceRepositoryPort();
    private final DeleteResourceUseCase useCase = new DeleteResourceUseCase(repository);

    @Test
    void disablesAResourceOwnedByTheGivenTenant() {
        TenantId tenantId = TenantId.generate();
        RateLimitResource resource = RateLimitResource.create(tenantId, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        Boolean deleted = useCase.delete(tenantId, resource.id()).block();

        assertThat(deleted).isTrue();
        assertThat(repository.findById(resource.id()).block().enabled()).isFalse();
    }

    @Test
    void returnsFalseWhenOwnedByADifferentTenant() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        RateLimitResource resource = RateLimitResource.create(owner, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        Boolean deleted = useCase.delete(other, resource.id()).block();

        assertThat(deleted).isFalse();
        assertThat(repository.findById(resource.id()).block().enabled()).isTrue();
    }
}
