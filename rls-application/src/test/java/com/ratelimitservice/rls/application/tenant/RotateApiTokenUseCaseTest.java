package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.FakeSecretHasherPort;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RotateApiTokenUseCaseTest {

    private final InMemoryTenantRepositoryPort repository = new InMemoryTenantRepositoryPort();
    private final RegisterTenantUseCase registerUseCase =
            new RegisterTenantUseCase(repository, new FakeSecretHasherPort());
    private final RotateApiTokenUseCase useCase = new RotateApiTokenUseCase(repository);

    @Test
    void rotatesTheActiveTokenAndPersistsTheChange() {
        RegisterTenantUseCase.Result registered =
                registerUseCase.register("Acme Inc", "rotate@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN).block();
        Tenant tenant = repository.findById(registered.tenantId()).block();

        String newRawToken = useCase.rotate(tenant).block();

        assertThat(newRawToken).startsWith("rls_live_").isNotEqualTo(registered.apiToken());

        Tenant reloaded = repository.findById(registered.tenantId()).block();
        assertThat(reloaded.apiTokens()).hasSize(2);
        boolean oldTokenRevoked = reloaded.apiTokens().stream()
                .filter(t -> t.tokenHash().equals(ApiTokenGenerator.fingerprint(registered.apiToken())))
                .findFirst().orElseThrow().revokedAt() != null;
        boolean newTokenActive = reloaded.apiTokens().stream()
                .filter(t -> t.tokenHash().equals(ApiTokenGenerator.fingerprint(newRawToken)))
                .findFirst().orElseThrow().isActive();

        assertThat(oldTokenRevoked).isTrue();
        assertThat(newTokenActive).isTrue();
    }
}
