package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@link RateLimitDecision} that is denied is required, by its own constructor invariant,
 * to carry a non-null retryAfter (see {@link RateLimitDecisionTest}). These tests drive each of
 * the five strategies into a denied decision to confirm that guarantee holds end to end for every
 * strategy, not just at the value object level.
 */
class AllStrategiesDenialRetryAfterTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    static Stream<DenialCase> deniedEvaluations() {
        Quota quota = Quota.withBurst(2, Duration.ofSeconds(10), 2);
        return Stream.of(
                new DenialCase(new FixedWindowStrategy(), new FixedWindowState(2, T0), quota),
                new DenialCase(new SlidingWindowLogStrategy(),
                        new SlidingLogState(List.of(T0, T0.plusSeconds(1))), quota),
                new DenialCase(new SlidingWindowCounterStrategy(),
                        new SlidingCounterState(2, 2, T0), quota),
                new DenialCase(new TokenBucketStrategy(), new TokenBucketState(0.0, T0), quota),
                new DenialCase(new LeakyBucketStrategy(), new LeakyBucketState(T0.plusSeconds(30)), quota)
        );
    }

    @ParameterizedTest
    @MethodSource("deniedEvaluations")
    void deniedDecisionAlwaysHasNonNullRetryAfter(DenialCase testCase) {
        RateLimitEvaluation evaluation = testCase.strategy().evaluate(testCase.state(), testCase.quota(), T0);

        assertThat(evaluation.decision().allowed())
                .as("test case for %s must actually produce a denied decision", testCase.strategy().type())
                .isFalse();
        assertThat(evaluation.decision().retryAfter()).isNotNull();
    }

    @Test
    void strategyRegistryCoversAllFiveStrategyTypes() {
        assertThat(deniedEvaluations().map(c -> c.strategy().type()).distinct().count()).isEqualTo(5);
    }

    private record DenialCase(RateLimitStrategy strategy, RateLimitState state, Quota quota) {
    }
}
