package com.ratelimitservice.rls.domain.ratelimit;

public enum StrategyType {
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    TOKEN_BUCKET,
    LEAKY_BUCKET
}
