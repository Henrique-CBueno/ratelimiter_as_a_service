package com.ratelimitservice.rls.application.resource;

import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateResourceUseCaseTest {

    private final InMemoryResourceRepositoryPort repository = new InMemoryResourceRepositoryPort();
    private final CreateResourceUseCase useCase = new CreateResourceUseCase(repository);
    private final TenantId tenantId = TenantId.generate();

    @Test
    void createsAndPersistsANewResource() {
        RateLimitResource resource = useCase
                .create(tenantId, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)), null)
                .block();

        assertThat(resource.id()).isNotNull();
        assertThat(repository.findById(resource.id()).block()).isNotNull();
    }

    @Test
    void propagatesDuplicateResourceKeyFailureFromThePort() {
        useCase.create(tenantId, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)), null).block();

        assertThatThrownBy(() -> useCase
                .create(tenantId, "/login", StrategyType.TOKEN_BUCKET, Quota.withBurst(5, Duration.ofSeconds(10), 5), null)
                .block())
                .isInstanceOf(DuplicateResourceKeyException.class);
    }
}
