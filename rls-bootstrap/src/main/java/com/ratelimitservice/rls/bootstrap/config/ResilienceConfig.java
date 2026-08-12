package com.ratelimitservice.rls.bootstrap.config;

import com.ratelimitservice.rls.adapter.redis.RedisRateLimitEvaluationAdapter;
import com.ratelimitservice.rls.adapter.resilience.ResilientRateLimitEvaluationAdapter;
import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The single global circuit breaker guarding {@link RateLimitEvaluationPort} (design decision 3),
 * decorating the raw Redis adapter (spec 2) so it's the only bean of this port type the
 * application ever sees — {@code CheckRateLimitUseCase} is unaware the circuit breaker exists.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker rateLimitEvaluationCircuitBreaker(
            @Value("${rls.resilience.failure-rate-threshold}") float failureRateThreshold,
            @Value("${rls.resilience.sliding-window-size}") int slidingWindowSize,
            @Value("${rls.resilience.minimum-number-of-calls}") int minimumNumberOfCalls,
            @Value("${rls.resilience.wait-duration-in-open-state-seconds}") long waitDurationInOpenStateSeconds) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenStateSeconds))
                .build();
        return CircuitBreaker.of("redis-rate-limit", config);
    }

    @Bean
    public RateLimitEvaluationPort rateLimitEvaluationPort(RedisRateLimitEvaluationAdapter delegate,
                                                             CircuitBreaker rateLimitEvaluationCircuitBreaker) {
        return new ResilientRateLimitEvaluationAdapter(delegate, rateLimitEvaluationCircuitBreaker);
    }
}
