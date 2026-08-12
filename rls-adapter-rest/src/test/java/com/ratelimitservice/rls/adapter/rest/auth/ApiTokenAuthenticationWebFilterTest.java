package com.ratelimitservice.rls.adapter.rest.auth;

import com.ratelimitservice.rls.application.tenant.AuthenticateTenantUseCase;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiTokenAuthenticationWebFilterTest {

    private final AuthenticateTenantUseCase authenticateTenantUseCase = mock(AuthenticateTenantUseCase.class);
    private final ApiTokenAuthenticationWebFilter filter = new ApiTokenAuthenticationWebFilter(authenticateTenantUseCase);

    private static WebFilterChain recordingChain(AtomicBoolean called) {
        return exchange -> {
            called.set(true);
            return Mono.empty();
        };
    }

    @Test
    void rejectsRequestsWithoutAnAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/resources"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled.get()).isFalse();
        verifyNoInteractions(authenticateTenantUseCase);
    }

    @Test
    void rejectsRequestsWithAnUnknownOrInvalidToken() {
        when(authenticateTenantUseCase.authenticate("bad-token")).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/resources").header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled.get()).isFalse();
    }

    @Test
    void passesThroughAndAttachesTheResolvedTenantForAValidToken() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
        when(authenticateTenantUseCase.authenticate("good-token")).thenReturn(Mono.just(tenant));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/resources").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled))).verifyComplete();

        assertThat(chainCalled.get()).isTrue();
        assertThat((Tenant) exchange.getAttribute(AuthenticatedTenant.ATTRIBUTE)).isEqualTo(tenant);
    }

    @Test
    void allowsTenantRegistrationWithoutAToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/tenants"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled))).verifyComplete();

        assertThat(chainCalled.get()).isTrue();
        verifyNoInteractions(authenticateTenantUseCase);
    }

    @Test
    void passesThroughPathsOutsideApiV1WithoutRequiringAToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/app/register"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled))).verifyComplete();

        assertThat(chainCalled.get()).isTrue();
        verifyNoInteractions(authenticateTenantUseCase);
    }
}
