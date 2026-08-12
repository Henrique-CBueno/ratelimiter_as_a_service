package com.ratelimitservice.rls.application.ratelimit;

import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort;
import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationUnavailableException;
import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

import java.time.Instant;

public final class CheckRateLimitUseCase {

    private final ResourceRepositoryPort resourceRepositoryPort;
    private final RateLimitEvaluationPort rateLimitEvaluationPort;

    public CheckRateLimitUseCase(ResourceRepositoryPort resourceRepositoryPort,
                                  RateLimitEvaluationPort rateLimitEvaluationPort) {
        this.resourceRepositoryPort = resourceRepositoryPort;
        this.rateLimitEvaluationPort = rateLimitEvaluationPort;
    }

    public Mono<RateLimitDecision> check(Tenant tenant, String resourceKey, ClientIp clientIp) {
        return resourceRepositoryPort.findByTenantAndKey(tenant.id(), resourceKey)
                .flatMap(resource -> evaluate(tenant, resource, clientIp));
    }

    private Mono<RateLimitDecision> evaluate(Tenant tenant, RateLimitResource resource, ClientIp clientIp) {
        RateLimitKey key = new RateLimitKey(tenant.id(), resource.id(), clientIp, resource.strategyType());
        return rateLimitEvaluationPort.evaluate(key, resource.quota())
                .onErrorResume(RateLimitEvaluationUnavailableException.class,
                        ex -> Mono.just(fallbackDecision(tenant, resource)));
    }

    private RateLimitDecision fallbackDecision(Tenant tenant, RateLimitResource resource) {
        FallbackPolicy policy = resource.resolveFallbackPolicy(tenant);
        Quota quota = resource.quota();
        Instant resetAt = Instant.now().plus(quota.window());

        RateLimitDecision decision = policy == FallbackPolicy.FAIL_OPEN
                ? RateLimitDecision.allow(quota.limit(), quota.limit(), resetAt)
                : RateLimitDecision.deny(quota.limit(), resetAt, quota.window());
        return decision.asDegraded();
    }
}
