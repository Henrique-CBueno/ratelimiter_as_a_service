package com.ratelimitservice.rls.bootstrap;

import com.ratelimitservice.rls.adapter.rest.ratelimit.CheckRequest;
import com.ratelimitservice.rls.adapter.rest.resource.CreateResourceRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantResponse;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full stack end to end against real Redis and PostgreSQL: register a tenant,
 * configure a resource, then call check repeatedly until the configured limit is exceeded.
 */
class HappyPathE2EIT extends AbstractE2ETest {

    @Test
    void registerCreateResourceAndExceedTheLimit() {
        RegisterTenantResponse registration = webTestClient.post().uri("/api/v1/tenants")
                .bodyValue(new RegisterTenantRequest("Acme Inc", "e2e@acme.test", "s3cret"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(RegisterTenantResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(registration).isNotNull();
        String token = registration.apiToken();

        webTestClient.post().uri("/api/v1/resources")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CreateResourceRequest("/login", StrategyType.FIXED_WINDOW, 3, 60, null, null))
                .exchange()
                .expectStatus().isCreated();

        for (int i = 1; i <= 3; i++) {
            webTestClient.post().uri("/api/v1/ratelimit/check")
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(new CheckRequest("/login", "203.0.113.99"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.allowed").isEqualTo(true)
                    .jsonPath("$.remaining").isEqualTo(3 - i);
        }

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CheckRequest("/login", "203.0.113.99"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(false);
    }
}
