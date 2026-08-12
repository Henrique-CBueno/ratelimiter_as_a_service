package com.ratelimitservice.rls.adapter.redis;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the empirical proof this whole spec exists for: firing more concurrent requests than
 * the configured limit against the same key must never let more than {@code limit} of them
 * through, for every strategy. A stateless, horizontally-scaled deployment relies on Redis's
 * atomic Lua execution — not client-side coordination — to guarantee this.
 */
class ConcurrencyRedisAdapterIT extends AbstractRedisIT {

    private static final int LIMIT = 20;
    private static final int CONCURRENT_REQUESTS = 100;

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void concurrentRequestsNeverExceedTheConfiguredLimit(StrategyType strategyType) {
        RateLimitKey key = new RateLimitKey(TenantId.generate(), ResourceId.generate(),
                new ClientIp("203.0.113.80"), strategyType);
        Quota quota = Quota.withBurst(LIMIT, Duration.ofSeconds(30), LIMIT);

        long allowedCount = Flux.range(0, CONCURRENT_REQUESTS)
                .flatMap(i -> adapter.evaluate(key, quota), CONCURRENT_REQUESTS)
                .filter(RateLimitDecision::allowed)
                .count()
                .block();

        assertThat(allowedCount)
                .as("exactly %d of %d concurrent requests should be allowed for %s", LIMIT, CONCURRENT_REQUESTS, strategyType)
                .isEqualTo(LIMIT);
    }
}
