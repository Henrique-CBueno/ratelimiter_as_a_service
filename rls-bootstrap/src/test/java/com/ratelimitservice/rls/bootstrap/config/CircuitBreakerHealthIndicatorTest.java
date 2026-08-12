package com.ratelimitservice.rls.bootstrap.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerHealthIndicatorTest {

    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit", CircuitBreakerConfig.ofDefaults());
    private final CircuitBreakerHealthIndicator indicator = new CircuitBreakerHealthIndicator(circuitBreaker);

    @Test
    void reportsUpWhenClosed() {
        Health health = indicator.health().block();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsUpWhenHalfOpen() {
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();

        Health health = indicator.health().block();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDegradedWhenOpen() {
        circuitBreaker.transitionToOpenState();

        Health health = indicator.health().block();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
    }
}
