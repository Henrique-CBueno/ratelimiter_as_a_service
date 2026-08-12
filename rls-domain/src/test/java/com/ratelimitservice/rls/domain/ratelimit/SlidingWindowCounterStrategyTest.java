package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCounterStrategyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Quota QUOTA = Quota.of(10, Duration.ofSeconds(60));

    private final SlidingWindowCounterStrategy strategy = new SlidingWindowCounterStrategy();

    @Test
    void firstRequestEverIsAllowed() {
        RateLimitEvaluation evaluation = strategy.evaluate(null, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isTrue();
        SlidingCounterState newState = (SlidingCounterState) evaluation.newState();
        assertThat(newState.currentWindowCount()).isEqualTo(1);
        assertThat(newState.previousWindowCount()).isEqualTo(0);
    }

    @Test
    void requestAllowedUnderWeightedEstimate() {
        // previous=8, current=0, halfway through the window -> estimate = 8*0.5 + 0 = 4 < 10
        SlidingCounterState state = new SlidingCounterState(8, 0, T0);
        Instant now = T0.plusSeconds(30);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().allowed()).isTrue();
        // after allowing: estimate = 8*0.5 + 1 = 5 -> remaining = 10 - 5 = 5
        assertThat(evaluation.decision().remaining()).isEqualTo(5);
        SlidingCounterState newState = (SlidingCounterState) evaluation.newState();
        assertThat(newState.currentWindowCount()).isEqualTo(1);
        assertThat(newState.previousWindowCount()).isEqualTo(8);
    }

    @Test
    void requestDeniedWhenWeightedEstimateReachesLimit() {
        // previous=10, current=5, halfway through the window -> estimate = 10*0.5 + 5 = 10 >= 10
        SlidingCounterState state = new SlidingCounterState(10, 5, T0);
        Instant now = T0.plusSeconds(30);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().allowed()).isFalse();
        SlidingCounterState newState = (SlidingCounterState) evaluation.newState();
        assertThat(newState.currentWindowCount()).isEqualTo(5);
    }

    @Test
    void windowRolloverAfterOneElapsedWindowShiftsCurrentIntoPrevious() {
        SlidingCounterState state = new SlidingCounterState(3, 7, T0);
        Instant now = T0.plusSeconds(65);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        SlidingCounterState newState = (SlidingCounterState) evaluation.newState();
        assertThat(newState.previousWindowCount()).isEqualTo(7);
        assertThat(newState.currentWindowStart()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void windowRolloverAfterMultipleElapsedWindowsResetsBothCounters() {
        SlidingCounterState state = new SlidingCounterState(3, 7, T0);
        Instant now = T0.plusSeconds(200);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        SlidingCounterState newState = (SlidingCounterState) evaluation.newState();
        assertThat(newState.previousWindowCount()).isEqualTo(0);
        assertThat(evaluation.decision().allowed()).isTrue();
    }
}
