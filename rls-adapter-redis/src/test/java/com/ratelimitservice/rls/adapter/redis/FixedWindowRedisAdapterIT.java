package com.ratelimitservice.rls.adapter.redis;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRedisAdapterIT extends AbstractRedisIT {

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);

    private static RateLimitKey newKey() {
        return new RateLimitKey(TenantId.generate(), ResourceId.generate(), new ClientIp("203.0.113.10"),
                StrategyType.FIXED_WINDOW);
    }

    @Test
    void allowsRequestsUnderTheLimit() {
        RateLimitKey key = newKey();
        Quota quota = Quota.of(3, Duration.ofSeconds(30));

        StepVerifier.create(adapter.evaluate(key, quota))
                .assertNext(decision -> {
                    assertThat(decision.allowed()).isTrue();
                    assertThat(decision.remaining()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void deniesRequestsOnceTheLimitIsReached() {
        RateLimitKey key = newKey();
        Quota quota = Quota.of(2, Duration.ofSeconds(30));

        adapter.evaluate(key, quota).block();
        adapter.evaluate(key, quota).block();
        RateLimitDecision third = adapter.evaluate(key, quota).block();

        assertThat(third.allowed()).isFalse();
        assertThat(third.remaining()).isEqualTo(0);
        assertThat(third.retryAfter()).isNotNull();
    }

    @Test
    void deniedRequestsDoNotConsumeCapacity() {
        RateLimitKey key = newKey();
        Quota quota = Quota.of(1, Duration.ofSeconds(30));

        adapter.evaluate(key, quota).block();
        adapter.evaluate(key, quota).block();
        RateLimitDecision third = adapter.evaluate(key, quota).block();

        assertThat(third.allowed()).isFalse();
        assertThat(third.remaining()).isEqualTo(0);
    }

    @Test
    void windowRolloverAllowsRequestsAgainAfterExpiry() {
        RateLimitKey key = newKey();
        Quota quota = Quota.of(1, Duration.ofSeconds(1));

        RateLimitDecision first = adapter.evaluate(key, quota).block();
        assertThat(first.allowed()).isTrue();

        RateLimitDecision second = adapter.evaluate(key, quota).block();
        assertThat(second.allowed()).isFalse();

        StepVerifier.create(reactor.core.publisher.Mono.delay(Duration.ofMillis(1200))
                        .then(adapter.evaluate(key, quota)))
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();
    }
}
