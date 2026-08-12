package com.ratelimitservice.rls.adapter.resilience;

import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort;
import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationUnavailableException;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

/**
 * Decorates a {@link RateLimitEvaluationPort} with a single shared circuit breaker (see design
 * decision 3: one global circuit per physical dependency, not one per tenant/resource). Every
 * failure — circuit-open or an actual delegate failure counted by the breaker — surfaces
 * uniformly as {@link RateLimitEvaluationUnavailableException}, so callers only need to handle
 * one failure mode regardless of the underlying cause.
 */
public final class ResilientRateLimitEvaluationAdapter implements RateLimitEvaluationPort {

    private final RateLimitEvaluationPort delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientRateLimitEvaluationAdapter(RateLimitEvaluationPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Mono<RateLimitDecision> evaluate(RateLimitKey key, Quota quota) {
        return delegate.evaluate(key, quota)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(ex -> new RateLimitEvaluationUnavailableException("Rate limit evaluation unavailable", ex));
    }
}
