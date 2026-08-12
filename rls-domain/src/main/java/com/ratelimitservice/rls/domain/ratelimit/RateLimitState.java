package com.ratelimitservice.rls.domain.ratelimit;

public sealed interface RateLimitState
        permits FixedWindowState, SlidingLogState, SlidingCounterState, TokenBucketState, LeakyBucketState {
}
