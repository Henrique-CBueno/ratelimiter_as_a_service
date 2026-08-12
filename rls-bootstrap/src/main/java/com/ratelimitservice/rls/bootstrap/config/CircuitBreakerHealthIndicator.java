package com.ratelimitservice.rls.bootstrap.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Mono;

/**
 * Reports the rate-limit evaluation circuit breaker's state under {@code /actuator/health}.
 * {@code OPEN} maps to a custom {@code DEGRADED} status rather than {@code DOWN} (design decision
 * 3): the app keeps serving requests via its fallback policy while the circuit is open, so nothing
 * is actually down — reporting {@code DOWN} would be misleading and could trigger restarts that
 * don't fix the underlying (Redis-side) problem.
 */
public class CircuitBreakerHealthIndicator implements ReactiveHealthIndicator {

    static final Status DEGRADED = new Status("DEGRADED");

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerHealthIndicator(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Mono<Health> health() {
        CircuitBreaker.State state = circuitBreaker.getState();
        Status status = state == CircuitBreaker.State.OPEN ? DEGRADED : Status.UP;
        return Mono.just(Health.status(status)
                .withDetail("state", state.name())
                .build());
    }
}
