package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;

public final class LeakyBucketStrategy implements RateLimitStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.LEAKY_BUCKET;
    }

    @Override
    public RateLimitEvaluation evaluate(RateLimitState currentState, Quota quota, Instant now) {
        LeakyBucketState state = currentState instanceof LeakyBucketState leakyBucketState
                ? leakyBucketState
                : LeakyBucketState.empty(now);

        Duration emissionInterval = quota.window().dividedBy(quota.limit());
        // tau is the burst tolerance: (burstCapacity - 1) emission intervals, so that exactly
        // burstCapacity requests (not burstCapacity + 1) are admissible in a zero-time burst.
        Duration tau = emissionInterval.multipliedBy(quota.effectiveBurstCapacity() - 1);

        Instant storedTat = state.theoreticalArrivalTime();
        Instant referenceTat = storedTat.isAfter(now) ? storedTat : now;
        Instant allowAt = referenceTat.minus(tau);

        if (!now.isBefore(allowAt)) {
            Instant newTat = referenceTat.plus(emissionInterval);
            Duration headroom = tau.minus(Duration.between(now, newTat));
            long remaining = headroom.isNegative() ? 0 : headroom.dividedBy(emissionInterval);
            int cappedRemaining = (int) Math.min(quota.limit(), remaining);
            RateLimitDecision decision = RateLimitDecision.allow(quota.limit(), cappedRemaining, newTat);
            return new RateLimitEvaluation(decision, new LeakyBucketState(newTat));
        }

        Duration retryAfter = Duration.between(now, allowAt);
        RateLimitDecision decision = RateLimitDecision.deny(quota.limit(), storedTat, retryAfter);
        return new RateLimitEvaluation(decision, state);
    }
}
