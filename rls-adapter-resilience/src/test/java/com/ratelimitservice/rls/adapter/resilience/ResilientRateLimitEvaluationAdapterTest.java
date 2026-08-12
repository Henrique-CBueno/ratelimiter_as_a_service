package com.ratelimitservice.rls.adapter.resilience;

import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationUnavailableException;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientRateLimitEvaluationAdapterTest {

    private static final RateLimitKey KEY = new RateLimitKey(
            TenantId.generate(), ResourceId.generate(), new ClientIp("203.0.113.1"), StrategyType.FIXED_WINDOW);
    private static final Quota QUOTA = Quota.of(10, Duration.ofMinutes(1));

    @Test
    void repeatedFailuresOpenTheCircuitAndFurtherCallsFailFastWithoutTheDelegate() {
        AtomicInteger delegateCalls = new AtomicInteger();
        // Mono.defer matters here: a real adapter (e.g. the Redis one) only performs I/O at
        // subscription time, so the circuit breaker's subscription-time gating actually prevents
        // the call. Incrementing eagerly (outside defer) would count every invocation of
        // evaluate() regardless of whether the circuit breaker let the subscription through.
        var failingDelegate = new com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort() {
            @Override
            public Mono<com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision> evaluate(RateLimitKey key, Quota quota) {
                return Mono.defer(() -> {
                    delegateCalls.incrementAndGet();
                    return Mono.error(new RuntimeException("redis down"));
                });
            }
        };

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(1)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-redis", config);

        ResilientRateLimitEvaluationAdapter adapter = new ResilientRateLimitEvaluationAdapter(failingDelegate, circuitBreaker);

        // Fire enough failing calls to reach the circuit breaker's minimum call volume; the exact
        // number of delegate invocations this takes is an internal Resilience4j accounting detail
        // (e.g. whether the call that trips the breaker is itself still counted), so this loop
        // over-provisions rather than asserting a precise count.
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> adapter.evaluate(KEY, QUOTA).block())
                    .isInstanceOf(RateLimitEvaluationUnavailableException.class);
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                break;
            }
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBeforeOpen = delegateCalls.get();

        // Once open, further calls must fail fast without reaching the delegate again.
        assertThatThrownBy(() -> adapter.evaluate(KEY, QUOTA).block())
                .isInstanceOf(RateLimitEvaluationUnavailableException.class);
        assertThat(delegateCalls.get()).isEqualTo(callsBeforeOpen);
    }
}
