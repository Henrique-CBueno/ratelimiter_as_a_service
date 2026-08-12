package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Instant;

public record SlidingCounterState(int previousWindowCount, int currentWindowCount, Instant currentWindowStart)
        implements RateLimitState {

    public static SlidingCounterState empty(Instant now) {
        return new SlidingCounterState(0, 0, now);
    }
}
