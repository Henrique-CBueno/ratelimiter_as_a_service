package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Instant;

public record LeakyBucketState(Instant theoreticalArrivalTime) implements RateLimitState {

    public static LeakyBucketState empty(Instant now) {
        return new LeakyBucketState(now);
    }
}
