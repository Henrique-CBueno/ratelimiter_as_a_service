package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowStrategyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Quota QUOTA = Quota.of(2, Duration.ofMinutes(1));

    private final FixedWindowStrategy strategy = new FixedWindowStrategy();

    @Test
    void firstRequestEverIsAllowed() {
        RateLimitEvaluation evaluation = strategy.evaluate(null, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isTrue();
        assertThat(evaluation.decision().remaining()).isEqualTo(1);
        FixedWindowState newState = (FixedWindowState) evaluation.newState();
        assertThat(newState.count()).isEqualTo(1);
        assertThat(newState.windowStart()).isEqualTo(T0);
    }

    @Test
    void requestAllowedWhileUnderLimit() {
        FixedWindowState state = new FixedWindowState(1, T0);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0.plusSeconds(10));

        assertThat(evaluation.decision().allowed()).isTrue();
        assertThat(evaluation.decision().remaining()).isEqualTo(0);
        assertThat(((FixedWindowState) evaluation.newState()).count()).isEqualTo(2);
    }

    @Test
    void requestDeniedAtLimitDoesNotIncrementCount() {
        FixedWindowState state = new FixedWindowState(2, T0);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0.plusSeconds(30));

        assertThat(evaluation.decision().allowed()).isFalse();
        assertThat(evaluation.decision().remaining()).isEqualTo(0);
        assertThat(((FixedWindowState) evaluation.newState()).count()).isEqualTo(2);
    }

    @Test
    void windowRolloverResetsCountBeforeEvaluating() {
        FixedWindowState state = new FixedWindowState(2, T0);
        Instant afterWindow = T0.plus(Duration.ofMinutes(1));

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, afterWindow);

        assertThat(evaluation.decision().allowed()).isTrue();
        FixedWindowState newState = (FixedWindowState) evaluation.newState();
        assertThat(newState.count()).isEqualTo(1);
        assertThat(newState.windowStart()).isEqualTo(afterWindow);
    }

    @Test
    void deniedDecisionIncludesRetryAfterUntilWindowReset() {
        FixedWindowState state = new FixedWindowState(2, T0);
        Instant now = T0.plusSeconds(20);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().retryAfter()).isEqualTo(Duration.ofSeconds(40));
        assertThat(evaluation.decision().resetAt()).isEqualTo(T0.plus(Duration.ofMinutes(1)));
    }
}
