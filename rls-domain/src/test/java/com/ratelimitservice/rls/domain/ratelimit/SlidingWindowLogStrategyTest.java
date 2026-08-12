package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLogStrategyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Quota QUOTA = Quota.of(2, Duration.ofMinutes(1));

    private final SlidingWindowLogStrategy strategy = new SlidingWindowLogStrategy();

    @Test
    void firstRequestEverIsAllowed() {
        RateLimitEvaluation evaluation = strategy.evaluate(null, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isTrue();
        assertThat(evaluation.decision().remaining()).isEqualTo(1);
        assertThat(((SlidingLogState) evaluation.newState()).timestamps()).containsExactly(T0);
    }

    @Test
    void requestAllowedWhenTrailingWindowHasCapacity() {
        SlidingLogState state = new SlidingLogState(List.of(T0));

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0.plusSeconds(10));

        assertThat(evaluation.decision().allowed()).isTrue();
        assertThat(evaluation.decision().remaining()).isEqualTo(0);
        assertThat(((SlidingLogState) evaluation.newState()).timestamps()).hasSize(2);
    }

    @Test
    void requestDeniedWhenTrailingWindowIsFull() {
        SlidingLogState state = new SlidingLogState(List.of(T0, T0.plusSeconds(5)));

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0.plusSeconds(10));

        assertThat(evaluation.decision().allowed()).isFalse();
        assertThat(((SlidingLogState) evaluation.newState()).timestamps()).containsExactly(T0, T0.plusSeconds(5));
    }

    @Test
    void expiredTimestampsArePurgedBeforeCounting() {
        Instant expired = T0;
        Instant stillValid = T0.plusSeconds(50);
        SlidingLogState state = new SlidingLogState(List.of(expired, stillValid));
        Instant now = T0.plus(Duration.ofMinutes(1)).plusSeconds(1);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().allowed()).isTrue();
        List<Instant> retained = ((SlidingLogState) evaluation.newState()).timestamps();
        assertThat(retained).doesNotContain(expired);
        assertThat(retained).contains(stillValid, now);
    }

    @Test
    void deniedDecisionRetryAfterMatchesOldestEntryExpiry() {
        SlidingLogState state = new SlidingLogState(List.of(T0, T0.plusSeconds(5)));
        Instant now = T0.plusSeconds(10);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        Instant expectedResetAt = T0.plus(Duration.ofMinutes(1));
        assertThat(evaluation.decision().resetAt()).isEqualTo(expectedResetAt);
        assertThat(evaluation.decision().retryAfter()).isEqualTo(Duration.between(now, expectedResetAt));
    }
}
