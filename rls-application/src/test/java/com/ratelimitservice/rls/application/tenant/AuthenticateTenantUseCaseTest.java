package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.FakeSecretHasherPort;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticateTenantUseCaseTest {

    private final InMemoryTenantRepositoryPort repository = new InMemoryTenantRepositoryPort();
    private final RegisterTenantUseCase registerUseCase =
            new RegisterTenantUseCase(repository, new FakeSecretHasherPort());
    private final AuthenticateTenantUseCase useCase = new AuthenticateTenantUseCase(repository);

    @Test
    void resolvesTheTenantForAValidActiveToken() {
        RegisterTenantUseCase.Result registered =
                registerUseCase.register("Acme Inc", "auth@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN).block();

        var tenant = useCase.authenticate(registered.apiToken()).block();

        assertThat(tenant).isNotNull();
        assertThat(tenant.id()).isEqualTo(registered.tenantId());
    }

    @Test
    void returnsEmptyForAnUnknownToken() {
        var tenant = useCase.authenticate("rls_live_does-not-exist").block();

        assertThat(tenant).isNull();
    }

    @Test
    void returnsEmptyForARevokedToken() {
        RegisterTenantUseCase.Result registered =
                registerUseCase.register("Acme Inc", "revoked@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN).block();
        var tenant = repository.findById(registered.tenantId()).block();
        tenant.rotateApiToken("new-hash", "new-prefix", java.time.Instant.now());
        repository.save(tenant).block();

        var found = useCase.authenticate(registered.apiToken()).block();

        assertThat(found).isNull();
    }
}
