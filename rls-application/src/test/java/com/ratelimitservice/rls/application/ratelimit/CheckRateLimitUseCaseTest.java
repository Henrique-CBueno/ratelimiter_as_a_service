package com.ratelimitservice.rls.application.ratelimit;

import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationUnavailableException;
import com.ratelimitservice.rls.application.resource.InMemoryResourceRepositoryPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CheckRateLimitUseCaseTest {

    private final InMemoryResourceRepositoryPort resourceRepository = new InMemoryResourceRepositoryPort();
    private final FakeRateLimitEvaluationPort evaluationPort = new FakeRateLimitEvaluationPort();
    private final CheckRateLimitUseCase useCase = new CheckRateLimitUseCase(resourceRepository, evaluationPort);

    private static Tenant newTenant(FallbackPolicy defaultPolicy) {
        return Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", defaultPolicy);
    }

    @Test
    void evaluatesAConfiguredResourceAndReturnsThePortsDecision() {
        Tenant tenant = newTenant(FallbackPolicy.FAIL_OPEN);
        RateLimitResource resource = RateLimitResource.create(tenant.id(), "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        resourceRepository.save(resource).block();
        RateLimitDecision expected = RateLimitDecision.allow(10, 9, Instant.parse("2026-01-01T00:00:00Z"));
        evaluationPort.willReturn(expected);

        RateLimitDecision decision = useCase.check(tenant, "/login", new ClientIp("203.0.113.1")).block();

        assertThat(decision).isEqualTo(expected);
        assertThat(evaluationPort.lastKey().tenantId()).isEqualTo(tenant.id());
        assertThat(evaluationPort.lastKey().resourceId()).isEqualTo(resource.id());
        assertThat(evaluationPort.lastKey().strategyType()).isEqualTo(StrategyType.FIXED_WINDOW);
    }

    @Test
    void returnsEmptyWhenTheResourceIsNotConfiguredForTheTenant() {
        Tenant tenant = newTenant(FallbackPolicy.FAIL_OPEN);

        RateLimitDecision decision = useCase.check(tenant, "/unknown", new ClientIp("203.0.113.1")).block();

        assertThat(decision).isNull();
    }

    @Test
    void fallsBackToAllowWhenEvaluationIsUnavailableAndPolicyIsFailOpen() {
        Tenant tenant = newTenant(FallbackPolicy.FAIL_OPEN);
        RateLimitResource resource = RateLimitResource.create(tenant.id(), "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
        resourceRepository.save(resource).block();
        evaluationPort.willFailWith(new RateLimitEvaluationUnavailableException("circuit open"));

        RateLimitDecision decision = useCase.check(tenant, "/login", new ClientIp("203.0.113.1")).block();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.degraded()).isTrue();
    }

    @Test
    void fallsBackToDenyWhenEvaluationIsUnavailableAndPolicyIsFailClosed() {
        Tenant tenant = newTenant(FallbackPolicy.FAIL_OPEN);
        RateLimitResource resource = RateLimitResource.create(tenant.id(), "/login", StrategyType.FIXED_WINDOW,
                Quota.of(10, Duration.ofMinutes(1)), FallbackPolicy.FAIL_CLOSED);
        resourceRepository.save(resource).block();
        evaluationPort.willFailWith(new RateLimitEvaluationUnavailableException("circuit open"));

        RateLimitDecision decision = useCase.check(tenant, "/login", new ClientIp("203.0.113.1")).block();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.degraded()).isTrue();
        assertThat(decision.retryAfter()).isNotNull();
    }
}
