package com.ratelimitservice.rls.adapter.rest.tenant;

import com.ratelimitservice.rls.adapter.rest.auth.AuthenticatedTenant;
import com.ratelimitservice.rls.adapter.rest.error.RestExceptionHandler;
import com.ratelimitservice.rls.application.tenant.RegisterTenantUseCase;
import com.ratelimitservice.rls.application.tenant.RotateApiTokenUseCase;
import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(TenantController.class)
@Import({RestExceptionHandler.class, TenantControllerTest.TestConfig.class})
class TenantControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegisterTenantUseCase registerTenantUseCase;

    @MockitoBean
    private RotateApiTokenUseCase rotateApiTokenUseCase;

    @Test
    void registrationReturnsCreatedWithTenantIdAndToken() {
        TenantId tenantId = TenantId.generate();
        when(registerTenantUseCase.register(any(), any(), any(), any()))
                .thenReturn(Mono.just(new RegisterTenantUseCase.Result(tenantId, "rls_live_abc")));

        webTestClient.post().uri("/api/v1/tenants")
                .bodyValue(new RegisterTenantRequest("Acme Inc", "ops@acme.test", "s3cret"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.tenantId").isEqualTo(tenantId.value().toString())
                .jsonPath("$.apiToken").isEqualTo("rls_live_abc");
    }

    @Test
    void registrationWithDuplicateEmailReturnsConflict() {
        when(registerTenantUseCase.register(any(), any(), any(), any()))
                .thenReturn(Mono.error(new DuplicateEmailException("dup@acme.test")));

        webTestClient.post().uri("/api/v1/tenants")
                .bodyValue(new RegisterTenantRequest("Acme Inc", "dup@acme.test", "s3cret"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void rotationReturnsOkWithNewToken() {
        when(rotateApiTokenUseCase.rotate(any())).thenReturn(Mono.just("rls_live_new"));

        webTestClient.post().uri("/api/v1/tokens/rotate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.apiToken").isEqualTo("rls_live_new");
    }

    /**
     * This slice only loads {@link TenantController}, not the real
     * {@code ApiTokenAuthenticationWebFilter} (group 7), so a stand-in filter injects a fixed
     * authenticated tenant for the rotate endpoint's test. Whether an actually-unauthenticated
     * request is rejected with 401 is the auth filter's own responsibility and is verified by its
     * own tests in group 7, not here.
     */
    static class TestConfig {
        @Bean
        WebFilter fakeAuthenticatedTenantFilter() {
            Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
            return (exchange, chain) -> {
                exchange.getAttributes().put(AuthenticatedTenant.ATTRIBUTE, tenant);
                return chain.filter(exchange);
            };
        }
    }
}
