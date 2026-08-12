package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Instant;

public interface RateLimitStrategy {

    StrategyType type();

    /**
     * {@code currentState} is {@code null} when this is the first request ever evaluated for the
     * key; implementations must treat that as their own type's empty/initial state.
     */
    RateLimitEvaluation evaluate(RateLimitState currentState, Quota quota, Instant now);
}
