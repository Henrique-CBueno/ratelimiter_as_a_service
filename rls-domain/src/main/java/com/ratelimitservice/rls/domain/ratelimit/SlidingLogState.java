package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Instant;
import java.util.List;

public record SlidingLogState(List<Instant> timestamps) implements RateLimitState {

    public SlidingLogState {
        timestamps = List.copyOf(timestamps);
    }

    public static SlidingLogState empty() {
        return new SlidingLogState(List.of());
    }
}
