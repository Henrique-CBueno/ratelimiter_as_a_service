package com.ratelimitservice.rls.bootstrap;

import com.ratelimitservice.rls.adapter.rest.ratelimit.CheckRequest;
import com.ratelimitservice.rls.adapter.rest.resource.CreateResourceRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantRequest;
import com.ratelimitservice.rls.adapter.rest.tenant.RegisterTenantResponse;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the `observability` capability's scenarios end to end against the real app context.
 * {@code @AutoConfigureObservability} is required: Spring Boot's test support disables metrics
 * export by default (`management.defaults.metrics.export.enabled=false`, applied via a
 * `ContextCustomizerFactory`, not a regular auto-configuration) to avoid every `@SpringBootTest`
 * paying the cost of real metric registration — this class specifically needs it re-enabled to
 * exercise `/actuator/prometheus`.
 */
@AutoConfigureObservability
class ActuatorE2EIT extends AbstractE2ETest {

    @Autowired
    private CircuitBreaker rateLimitEvaluationCircuitBreaker;

    @AfterEach
    void closeCircuit() {
        rateLimitEvaluationCircuitBreaker.transitionToClosedState();
    }

    @Test
    void healthReportsUpWhenCircuitIsClosed() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components.circuitBreaker.status").isEqualTo("UP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthReportsDegradedForTheCircuitBreakerComponentWhenOpen() {
        rateLimitEvaluationCircuitBreaker.transitionToOpenState();

        Map<String, Object> body = webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Map<String, Object> components = (Map<String, Object>) body.get("components");
        Map<String, Object> circuitBreakerComponent = (Map<String, Object>) components.get("circuitBreaker");
        assertThat(circuitBreakerComponent.get("status")).isEqualTo("DEGRADED");
        assertThat(body.get("status")).isNotEqualTo("UP");

        rateLimitEvaluationCircuitBreaker.transitionToClosedState();

        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.circuitBreaker.status").isEqualTo("UP");
    }

    @Test
    void prometheusEndpointExposesCircuitBreakerMetrics() {
        RegisterTenantResponse registration = webTestClient.post().uri("/api/v1/tenants")
                .bodyValue(new RegisterTenantRequest("Acme Inc", "actuator-e2e@acme.test", "s3cret"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(RegisterTenantResponse.class)
                .returnResult()
                .getResponseBody();
        String token = registration.apiToken();

        webTestClient.post().uri("/api/v1/resources")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CreateResourceRequest("/metrics-check", StrategyType.FIXED_WINDOW, 10, 60, null, null))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .header("Authorization", "Bearer " + token)
                .bodyValue(new CheckRequest("/metrics-check", "203.0.113.10"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody()).contains("resilience4j_circuitbreaker"));
    }

    @Test
    void unlistedActuatorEndpointsAreNotReachable() {
        webTestClient.get().uri("/actuator/env")
                .exchange()
                .expectStatus().isNotFound();
    }
}
