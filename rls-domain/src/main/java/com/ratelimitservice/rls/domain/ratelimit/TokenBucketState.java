package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Instant;

public record TokenBucketState(double tokens, Instant lastRefill) implements RateLimitState {

    public static TokenBucketState full(Quota quota, Instant now) {
        return new TokenBucketState(quota.effectiveBurstCapacity(), now);
    }
}
