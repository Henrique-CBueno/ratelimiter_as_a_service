package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.FakeSecretHasherPort;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetTenantUseCaseTest {

    private final InMemoryTenantRepositoryPort repository = new InMemoryTenantRepositoryPort();
    private final FakeSecretHasherPort hasher = new FakeSecretHasherPort();
    private final GetTenantUseCase useCase = new GetTenantUseCase(repository);

    @Test
    void returnsThePersistedTenantById() {
        RegisterTenantUseCase.Result result = new RegisterTenantUseCase(repository, hasher)
                .register("Acme Inc", "ops@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN)
                .block();

        Tenant tenant = useCase.get(result.tenantId()).block();

        assertThat(tenant).isNotNull();
        assertThat(tenant.email()).isEqualTo("ops@acme.test");
    }

    @Test
    void returnsEmptyForAnUnknownTenantId() {
        Tenant tenant = useCase.get(TenantId.generate()).blockOptional().orElse(null);

        assertThat(tenant).isNull();
    }
}
