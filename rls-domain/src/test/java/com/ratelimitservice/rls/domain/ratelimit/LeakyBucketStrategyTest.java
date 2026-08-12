package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LeakyBucketStrategyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    // limit=5 per 5s window => emission interval of 1s; burst capacity 5 => tau of 5s.
    private static final Quota QUOTA = Quota.withBurst(5, Duration.ofSeconds(5), 5);

    private final LeakyBucketStrategy strategy = new LeakyBucketStrategy();

    @Test
    void firstRequestEverIsAllowedFromAnEmptyBucket() {
        RateLimitEvaluation evaluation = strategy.evaluate(null, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isTrue();
        LeakyBucketState newState = (LeakyBucketState) evaluation.newState();
        assertThat(newState.theoreticalArrivalTime()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void requestAllowedExactlyAtTheAllowedBoundary() {
        // tau = (burstCapacity - 1) * emissionInterval = 4 * 1s = 4s.
        // TAT is exactly tau ahead of now -> allowAt == now, boundary is inclusive.
        LeakyBucketState state = new LeakyBucketState(T0.plusSeconds(4));

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isTrue();
        LeakyBucketState newState = (LeakyBucketState) evaluation.newState();
        assertThat(newState.theoreticalArrivalTime()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void requestDeniedWhenItArrivesBeforeTheAllowedTime() {
        // TAT is 6s ahead of now, beyond the 4s burst tolerance (tau) -> must wait 2s.
        LeakyBucketState state = new LeakyBucketState(T0.plusSeconds(6));

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0);

        assertThat(evaluation.decision().allowed()).isFalse();
        assertThat(evaluation.decision().retryAfter()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void deniedRequestLeavesStoredTatUnchanged() {
        Instant originalTat = T0.plusSeconds(6);
        LeakyBucketState state = new LeakyBucketState(originalTat);

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, T0);

        LeakyBucketState newState = (LeakyBucketState) evaluation.newState();
        assertThat(newState.theoreticalArrivalTime()).isEqualTo(originalTat);
    }

    @Test
    void allowedRequestAdvancesTatByOneEmissionInterval() {
        LeakyBucketState state = new LeakyBucketState(T0.minusSeconds(10));
        Instant now = T0;

        RateLimitEvaluation evaluation = strategy.evaluate(state, QUOTA, now);

        assertThat(evaluation.decision().allowed()).isTrue();
        LeakyBucketState newState = (LeakyBucketState) evaluation.newState();
        // TAT was in the past, so the reference point becomes "now", plus one emission interval.
        assertThat(newState.theoreticalArrivalTime()).isEqualTo(now.plusSeconds(1));
    }
}
