package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.FakeSecretHasherPort;
import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterTenantUseCaseTest {

    private final InMemoryTenantRepositoryPort repository = new InMemoryTenantRepositoryPort();
    private final FakeSecretHasherPort hasher = new FakeSecretHasherPort();
    private final RegisterTenantUseCase useCase = new RegisterTenantUseCase(repository, hasher);

    @Test
    void registersANewTenantAndReturnsARawTokenOnce() {
        RegisterTenantUseCase.Result result = useCase
                .register("Acme Inc", "ops@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN)
                .block();

        assertThat(result.tenantId()).isNotNull();
        assertThat(result.apiToken()).startsWith("rls_live_");

        Tenant saved = repository.findById(result.tenantId()).block();
        assertThat(saved.email()).isEqualTo("ops@acme.test");
        assertThat(saved.passwordHash()).isEqualTo("hashed:s3cret");
        assertThat(saved.apiTokens()).hasSize(1);
        assertThat(saved.apiTokens().get(0).tokenHash()).isEqualTo(ApiTokenGenerator.fingerprint(result.apiToken()));
        assertThat(saved.apiTokens().get(0).isActive()).isTrue();
    }

    @Test
    void propagatesDuplicateEmailFailureFromThePort() {
        useCase.register("Acme Inc", "dup@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN).block();

        assertThatThrownBy(() -> useCase.register("Other Inc", "dup@acme.test", "other", FallbackPolicy.FAIL_OPEN).block())
                .isInstanceOf(DuplicateEmailException.class);
    }
}
