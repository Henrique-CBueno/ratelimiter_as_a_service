package com.ratelimitservice.rls.bootstrap;

import com.ratelimitservice.rls.adapter.rest.ratelimit.CheckRequest;
import com.ratelimitservice.rls.adapter.rest.resource.CreateResourceRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantResponse;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design decision 3's risk: a second {@code SecurityWebFilterChain} scoped to {@code /api/v1/**}
 * could accidentally start enforcing CSRF or session auth on REST requests. This proves the REST
 * API still works exactly as before — bearer token only, no CSRF token, no session cookie — with
 * both the web module's security chains active in the same running application.
 */
class RestApiUnaffectedRegressionE2EIT extends AbstractE2ETest {

    @Test
    void restApiWorksWithoutCsrfOrSessionEvenWithWebSecurityChainsActive() {
        RegisterTenantResponse registration = webTestClient.post().uri("/api/v1/tenants")
                .bodyValue(new RegisterTenantRequest("Acme Inc", "rest-regression@acme.test", "s3cret"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(RegisterTenantResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(registration).isNotNull();
        String token = registration.apiToken();

        webTestClient.post().uri("/api/v1/resources")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CreateResourceRequest("/regression", StrategyType.FIXED_WINDOW, 5, 60, null, null))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CheckRequest("/regression", "203.0.113.42"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(true);
    }
}
