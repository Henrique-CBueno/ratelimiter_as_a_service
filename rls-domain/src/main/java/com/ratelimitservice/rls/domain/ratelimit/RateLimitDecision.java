package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        Instant resetAt,
        Duration retryAfter,
        boolean degraded) {

    public RateLimitDecision {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative");
        }
        Objects.requireNonNull(resetAt, "resetAt must not be null");
        if (!allowed && retryAfter == null) {
            throw new IllegalArgumentException("retryAfter is required when the request is denied");
        }
    }

    public static RateLimitDecision allow(int limit, int remaining, Instant resetAt) {
        return new RateLimitDecision(true, limit, remaining, resetAt, null, false);
    }

    public static RateLimitDecision deny(int limit, Instant resetAt, Duration retryAfter) {
        return new RateLimitDecision(false, limit, 0, resetAt, retryAfter, false);
    }

    public RateLimitDecision asDegraded() {
        return new RateLimitDecision(allowed, limit, remaining, resetAt, retryAfter, true);
    }
}
