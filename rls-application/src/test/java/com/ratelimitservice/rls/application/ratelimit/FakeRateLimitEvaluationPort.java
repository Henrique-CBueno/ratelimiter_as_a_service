package com.ratelimitservice.rls.application.ratelimit;

import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import reactor.core.publisher.Mono;

public final class FakeRateLimitEvaluationPort implements RateLimitEvaluationPort {

    private RateLimitDecision nextDecision;
    private RuntimeException nextError;
    private RateLimitKey lastKey;
    private Quota lastQuota;

    public void willReturn(RateLimitDecision decision) {
        this.nextDecision = decision;
        this.nextError = null;
    }

    public void willFailWith(RuntimeException error) {
        this.nextError = error;
        this.nextDecision = null;
    }

    @Override
    public Mono<RateLimitDecision> evaluate(RateLimitKey key, Quota quota) {
        this.lastKey = key;
        this.lastQuota = quota;
        if (nextError != null) {
            return Mono.error(nextError);
        }
        return Mono.just(nextDecision);
    }

    public RateLimitKey lastKey() {
        return lastKey;
    }

    public Quota lastQuota() {
        return lastQuota;
    }
}
