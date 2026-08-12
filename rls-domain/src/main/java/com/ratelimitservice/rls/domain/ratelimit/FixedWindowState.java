package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Instant;

public record FixedWindowState(int count, Instant windowStart) implements RateLimitState {

    public static FixedWindowState empty(Instant now) {
        return new FixedWindowState(0, now);
    }
}
