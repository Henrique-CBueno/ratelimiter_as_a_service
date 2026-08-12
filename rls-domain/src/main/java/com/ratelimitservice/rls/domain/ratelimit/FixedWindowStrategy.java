package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;

public final class FixedWindowStrategy implements RateLimitStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.FIXED_WINDOW;
    }

    @Override
    public RateLimitEvaluation evaluate(RateLimitState currentState, Quota quota, Instant now) {
        FixedWindowState state = currentState instanceof FixedWindowState fixedWindowState
                ? fixedWindowState
                : FixedWindowState.empty(now);

        Instant windowEnd = state.windowStart().plus(quota.window());
        if (!now.isBefore(windowEnd)) {
            state = FixedWindowState.empty(now);
            windowEnd = now.plus(quota.window());
        }

        if (state.count() < quota.limit()) {
            FixedWindowState newState = new FixedWindowState(state.count() + 1, state.windowStart());
            RateLimitDecision decision = RateLimitDecision.allow(quota.limit(), quota.limit() - newState.count(), windowEnd);
            return new RateLimitEvaluation(decision, newState);
        }

        RateLimitDecision decision = RateLimitDecision.deny(quota.limit(), windowEnd, Duration.between(now, windowEnd));
        return new RateLimitEvaluation(decision, state);
    }
}
