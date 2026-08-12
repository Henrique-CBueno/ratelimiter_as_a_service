package com.ratelimitservice.rls.application.ratelimit.port;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import reactor.core.publisher.Mono;

public interface RateLimitEvaluationPort {

    /**
     * {@code key.strategyType()} determines which algorithm evaluates the request; it is not
     * passed separately since {@link RateLimitKey} already carries it.
     */
    Mono<RateLimitDecision> evaluate(RateLimitKey key, Quota quota);
}
