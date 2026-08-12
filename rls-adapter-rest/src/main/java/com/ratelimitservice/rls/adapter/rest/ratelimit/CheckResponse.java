package com.ratelimitservice.rls.adapter.rest.ratelimit;

import com.ratelimitservice.rls.domain.ratelimit.StrategyType;

import java.time.Instant;

public record CheckResponse(boolean allowed, int limit, int remaining, Instant resetAt,
                             Long retryAfterSeconds, StrategyType strategy) {
}
