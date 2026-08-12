package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;
import java.time.Instant;

public final class TokenBucketStrategy implements RateLimitStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.TOKEN_BUCKET;
    }

    @Override
    public RateLimitEvaluation evaluate(RateLimitState currentState, Quota quota, Instant now) {
        TokenBucketState state = currentState instanceof TokenBucketState tokenBucketState
                ? tokenBucketState
                : TokenBucketState.full(quota, now);

        double capacity = quota.effectiveBurstCapacity();
        double refillRatePerNano = (double) quota.limit() / quota.window().toNanos();
        long elapsedNanos = Math.max(0, Duration.between(state.lastRefill(), now).toNanos());
        double refilled = Math.min(capacity, state.tokens() + elapsedNanos * refillRatePerNano);

        if (refilled >= 1.0) {
            double remainingTokens = refilled - 1.0;
            TokenBucketState newState = new TokenBucketState(remainingTokens, now);
            long nanosToFull = (long) ((capacity - remainingTokens) / refillRatePerNano);
            Instant resetAt = now.plusNanos(Math.max(0, nanosToFull));
            RateLimitDecision decision = RateLimitDecision.allow(quota.limit(), (int) remainingTokens, resetAt);
            return new RateLimitEvaluation(decision, newState);
        }

        TokenBucketState newState = new TokenBucketState(refilled, now);
        long nanosToNextToken = (long) ((1.0 - refilled) / refillRatePerNano);
        Instant resetAt = now.plusNanos(Math.max(0, nanosToNextToken));
        RateLimitDecision decision = RateLimitDecision.deny(quota.limit(), resetAt, Duration.between(now, resetAt));
        return new RateLimitEvaluation(decision, newState);
    }
}
