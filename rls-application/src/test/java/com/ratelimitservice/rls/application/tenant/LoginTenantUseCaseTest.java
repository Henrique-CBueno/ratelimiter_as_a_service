package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.FakeSecretHasherPort;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginTenantUseCaseTest {

    private final InMemoryTenantRepositoryPort repository = new InMemoryTenantRepositoryPort();
    private final FakeSecretHasherPort hasher = new FakeSecretHasherPort();
    private final LoginTenantUseCase useCase = new LoginTenantUseCase(repository, hasher);

    @Test
    void logsInATenantWithMatchingEmailAndPassword() {
        new RegisterTenantUseCase(repository, hasher)
                .register("Acme Inc", "ops@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN)
                .block();

        Tenant tenant = useCase.login("ops@acme.test", "s3cret").block();

        assertThat(tenant).isNotNull();
        assertThat(tenant.email()).isEqualTo("ops@acme.test");
    }

    @Test
    void failsWithoutErrorWhenTheEmailHasNoMatchingTenant() {
        Tenant tenant = useCase.login("nobody@acme.test", "s3cret").blockOptional().orElse(null);

        assertThat(tenant).isNull();
    }

    @Test
    void failsWithoutErrorWhenThePasswordDoesNotMatch() {
        new RegisterTenantUseCase(repository, hasher)
                .register("Acme Inc", "ops@acme.test", "s3cret", FallbackPolicy.FAIL_OPEN)
                .block();

        Tenant tenant = useCase.login("ops@acme.test", "wrong").blockOptional().orElse(null);

        assertThat(tenant).isNull();
    }
}
