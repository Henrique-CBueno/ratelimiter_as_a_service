package com.ratelimitservice.rls.bootstrap;

import com.ratelimitservice.rls.adapter.rest.ratelimit.CheckRequest;
import com.ratelimitservice.rls.adapter.rest.resource.CreateResourceRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantResponse;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Forces the shared {@link CircuitBreaker} bean open directly rather than pausing the Redis
 * container: transitioning state programmatically is deterministic, whereas pausing a container
 * races against Lettuce's own connection timeouts and retries.
 */
class CircuitOpenFallbackE2EIT extends AbstractE2ETest {

    @Autowired
    private CircuitBreaker rateLimitEvaluationCircuitBreaker;

    @AfterEach
    void closeCircuit() {
        rateLimitEvaluationCircuitBreaker.transitionToClosedState();
    }

    @Test
    void failClosedResourceIsDeniedWhileCircuitIsOpen() {
        RegisterTenantResponse registration = webTestClient.post().uri("/api/v1/tenants")
                .bodyValue(new RegisterTenantRequest("Degraded Co", "degraded@acme.test", "s3cret"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(RegisterTenantResponse.class)
                .returnResult()
                .getResponseBody();

        String token = registration.apiToken();

        webTestClient.post().uri("/api/v1/resources")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CreateResourceRequest("/checkout", StrategyType.FIXED_WINDOW, 10, 60,
                        null, FallbackPolicy.FAIL_CLOSED))
                .exchange()
                .expectStatus().isCreated();

        rateLimitEvaluationCircuitBreaker.transitionToOpenState();

        // Well within the configured limit of 10 - would be 200/allowed under a healthy circuit,
        // so a 429 here can only come from the FAIL_CLOSED fallback kicking in.
        webTestClient.post().uri("/api/v1/ratelimit/check")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CheckRequest("/checkout", "198.51.100.7"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(false);

        rateLimitEvaluationCircuitBreaker.transitionToClosedState();

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CheckRequest("/checkout", "198.51.100.7"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(true);
    }
}
