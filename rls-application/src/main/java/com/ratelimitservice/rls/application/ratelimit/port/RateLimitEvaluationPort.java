package com.ratelimitservice.rls.application.ratelimit.port;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import reactor.core.publisher.Mono;

public interface RateLimitEvaluationPort {

    Mono<RateLimitDecision> evaluate(RateLimitKey key, StrategyType strategyType, Quota quota);
}
