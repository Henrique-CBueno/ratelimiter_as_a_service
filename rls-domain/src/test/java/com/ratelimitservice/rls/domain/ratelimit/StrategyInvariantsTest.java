package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven edge-case coverage shared by all five strategies: firing a burst of requests at
 * the very same instant (no time elapses between them) must never allow more than {@code limit}
 * of them, and {@code remaining} must never go negative, regardless of which algorithm is used.
 */
class StrategyInvariantsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final int LIMIT = 5;
    private static final Quota QUOTA = Quota.of(LIMIT, Duration.ofSeconds(10));

    static Stream<RateLimitStrategy> strategies() {
        return Stream.of(
                new FixedWindowStrategy(),
                new SlidingWindowLogStrategy(),
                new SlidingWindowCounterStrategy(),
                new TokenBucketStrategy(),
                new LeakyBucketStrategy()
        );
    }

    @ParameterizedTest
    @MethodSource("strategies")
    void allowedNeverExceedsLimitAndRemainingNeverNegativeUnderABurstAtTheSameInstant(RateLimitStrategy strategy) {
        RateLimitState state = null;
        int allowedCount = 0;

        for (int i = 0; i < LIMIT * 4; i++) {
            RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0);

            assertThat(evaluation.decision().remaining())
                    .as("remaining must never be negative for %s at iteration %d", strategy.type(), i)
                    .isGreaterThanOrEqualTo(0);

            if (evaluation.decision().allowed()) {
                allowedCount++;
            }
            state = evaluation.newState();
        }

        assertThat(allowedCount)
                .as("allowed count must never exceed the configured limit for %s", strategy.type())
                .isLessThanOrEqualTo(LIMIT);
    }
}
