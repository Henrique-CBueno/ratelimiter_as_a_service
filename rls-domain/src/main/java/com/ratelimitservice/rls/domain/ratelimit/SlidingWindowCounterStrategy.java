package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;

public final class SlidingWindowCounterStrategy implements RateLimitStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.SLIDING_WINDOW_COUNTER;
    }

    @Override
    public RateLimitEvaluation evaluate(RateLimitState currentState, Quota quota, Instant now) {
        SlidingCounterState state = currentState instanceof SlidingCounterState slidingCounterState
                ? slidingCounterState
                : SlidingCounterState.empty(now);

        state = rollWindowIfNeeded(state, quota, now);

        double elapsedFraction = elapsedFraction(state.currentWindowStart(), quota.window(), now);
        Instant resetAt = state.currentWindowStart().plus(quota.window());
        double estimatedBeforeThisRequest = state.previousWindowCount() * (1 - elapsedFraction) + state.currentWindowCount();

        if (estimatedBeforeThisRequest < quota.limit()) {
            SlidingCounterState newState = new SlidingCounterState(
                    state.previousWindowCount(), state.currentWindowCount() + 1, state.currentWindowStart());
            double estimatedAfterThisRequest = state.previousWindowCount() * (1 - elapsedFraction) + newState.currentWindowCount();
            int remaining = Math.max(0, (int) (quota.limit() - estimatedAfterThisRequest));
            RateLimitDecision decision = RateLimitDecision.allow(quota.limit(), remaining, resetAt);
            return new RateLimitEvaluation(decision, newState);
        }

        RateLimitDecision decision = RateLimitDecision.deny(quota.limit(), resetAt, Duration.between(now, resetAt));
        return new RateLimitEvaluation(decision, state);
    }

    private static SlidingCounterState rollWindowIfNeeded(SlidingCounterState state, Quota quota, Instant now) {
        Duration window = quota.window();
        Instant windowEnd = state.currentWindowStart().plus(window);
        if (now.isBefore(windowEnd)) {
            return state;
        }
        long elapsedWindows = Duration.between(state.currentWindowStart(), now).dividedBy(window);
        if (elapsedWindows == 1) {
            Instant newWindowStart = state.currentWindowStart().plus(window);
            return new SlidingCounterState(state.currentWindowCount(), 0, newWindowStart);
        }
        Instant newWindowStart = state.currentWindowStart().plus(window.multipliedBy(elapsedWindows));
        return new SlidingCounterState(0, 0, newWindowStart);
    }

    private static double elapsedFraction(Instant windowStart, Duration window, Instant now) {
        double elapsedNanos = Duration.between(windowStart, now).toNanos();
        double windowNanos = window.toNanos();
        return Math.min(1.0, Math.max(0.0, elapsedNanos / windowNanos));
    }
}
