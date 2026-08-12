package com.ratelimitservice.rls.adapter.rest.auth;

import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;

/**
 * Test-support stand-in for {@link ApiTokenAuthenticationWebFilter}: controller slice tests
 * (`@WebFluxTest`) only load the controller under test, not the real auth filter, so this
 * unconditionally attaches a fixed authenticated tenant. Verifying actually-unauthenticated
 * requests are rejected is {@link ApiTokenAuthenticationWebFilterTest}'s job, not any
 * controller slice's.
 */
public class FakeAuthenticatedTenantFilterConfig {

    public static final Tenant TENANT = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);

    @Bean
    WebFilter fakeAuthenticatedTenantFilter() {
        return (exchange, chain) -> {
            exchange.getAttributes().put(AuthenticatedTenant.ATTRIBUTE, TENANT);
            return chain.filter(exchange);
        };
    }
}
