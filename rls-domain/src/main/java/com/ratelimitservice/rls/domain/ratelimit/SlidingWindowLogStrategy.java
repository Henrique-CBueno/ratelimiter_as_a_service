package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SlidingWindowLogStrategy implements RateLimitStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.SLIDING_WINDOW_LOG;
    }

    @Override
    public RateLimitEvaluation evaluate(RateLimitState currentState, Quota quota, Instant now) {
        SlidingLogState state = currentState instanceof SlidingLogState slidingLogState
                ? slidingLogState
                : SlidingLogState.empty();

        Instant cutoff = now.minus(quota.window());
        List<Instant> retained = state.timestamps().stream()
                .filter(timestamp -> !timestamp.isBefore(cutoff))
                .sorted()
                .toList();

        if (retained.size() < quota.limit()) {
            List<Instant> updated = new ArrayList<>(retained);
            updated.add(now);
            Instant resetAt = updated.get(0).plus(quota.window());
            RateLimitDecision decision = RateLimitDecision.allow(quota.limit(), quota.limit() - updated.size(), resetAt);
            return new RateLimitEvaluation(decision, new SlidingLogState(updated));
        }

        Instant oldestRetained = retained.get(0);
        Instant resetAt = oldestRetained.plus(quota.window());
        RateLimitDecision decision = RateLimitDecision.deny(quota.limit(), resetAt, Duration.between(now, resetAt));
        return new RateLimitEvaluation(decision, new SlidingLogState(retained));
    }
}
