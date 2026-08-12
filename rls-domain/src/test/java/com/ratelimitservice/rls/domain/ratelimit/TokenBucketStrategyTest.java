package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokenBucketStrategyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    // 5 tokens per 5 seconds => refill rate of exactly 1 token/second.
    private static final Quota QUOTA = Quota.withBurst(5, Duration.ofSeconds(5), 5);

    private final TokenBucketStrategy strategy = new TokenBucketStrategy();

    @Test
    void firstRequestEverStartsFromAFullBucketAndIsAllowed() {
        RateLimitEvaluation evaluation = strategy.evaluate(null, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isTrue();
        TokenBucketState newState = (TokenBucketState) evaluation.newState();
        assertThat(newState.tokens()).isCloseTo(4.0, within(0.001));
    }

    @Test
    void requestAllowedWhenTokensAvailableAfterRefill() {
        TokenBucketState state = new TokenBucketState(0.0, T0);
        Instant now = T0.plusSeconds(2);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().allowed()).isTrue();
        TokenBucketState newState = (TokenBucketState) evaluation.newState();
        // refilled to 2 tokens after 2s, minus 1 consumed = 1 remaining
        assertThat(newState.tokens()).isCloseTo(1.0, within(0.001));
        assertThat(newState.lastRefill()).isEqualTo(now);
    }

    @Test
    void requestDeniedWhenNoTokensAvailableAfterRefill() {
        TokenBucketState state = new TokenBucketState(0.0, T0);
        Instant now = T0;

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().allowed()).isFalse();
        TokenBucketState newState = (TokenBucketState) evaluation.newState();
        assertThat(newState.tokens()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void refillNeverExceedsCapacity() {
        TokenBucketState state = new TokenBucketState(5.0, T0);
        Instant now = T0.plusSeconds(100);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        TokenBucketState newState = (TokenBucketState) evaluation.newState();
        // capacity is 5; after consuming 1, at most 4 remain, never above capacity - 1
        assertThat(newState.tokens()).isLessThanOrEqualTo(5.0);
        assertThat(evaluation.decision().allowed()).isTrue();
    }

    @Test
    void deniedDecisionHasNonNullRetryAfter() {
        TokenBucketState state = new TokenBucketState(0.0, T0);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0);

        assertThat(evaluation.decision().retryAfter()).isNotNull();
        assertThat(evaluation.decision().retryAfter()).isCloseTo(Duration.ofSeconds(1), Duration.ofMillis(1));
    }
}
