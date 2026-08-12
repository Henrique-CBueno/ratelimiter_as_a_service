package com.ratelimitservice.rls.domain.ratelimit;

import java.util.Objects;

public record RateLimitEvaluation(RateLimitDecision decision, RateLimitState newState) {

    public RateLimitEvaluation {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
    }
}
