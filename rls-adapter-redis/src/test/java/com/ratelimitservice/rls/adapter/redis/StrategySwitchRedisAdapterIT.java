package com.ratelimitservice.rls.adapter.redis;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Confirms the "strategy-scoped key isolation" requirement: since each strategy uses a different
 * Redis data structure (STRING, ZSET, HASH) under the strategy-qualified key from
 * {@link RedisKeyBuilder}, switching a resource's configured strategy must never make an
 * evaluation collide with state left behind by a previously configured strategy.
 */
class StrategySwitchRedisAdapterIT extends AbstractRedisIT {

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);

    @Test
    void switchingStrategyForTheSameTenantResourceAndIpStartsFreshWithoutError() {
        TenantId tenantId = TenantId.generate();
        ResourceId resourceId = ResourceId.generate();
        ClientIp clientIp = new ClientIp("203.0.113.60");
        Quota quota = Quota.withBurst(3, Duration.ofSeconds(30), 3);

        RateLimitKey fixedWindowKey = new RateLimitKey(tenantId, resourceId, clientIp, StrategyType.FIXED_WINDOW);
        RateLimitKey slidingLogKey = new RateLimitKey(tenantId, resourceId, clientIp, StrategyType.SLIDING_WINDOW_LOG);
        RateLimitKey hashBackedKey = new RateLimitKey(tenantId, resourceId, clientIp, StrategyType.SLIDING_WINDOW_COUNTER);
        RateLimitKey stringBackedKey = new RateLimitKey(tenantId, resourceId, clientIp, StrategyType.LEAKY_BUCKET);

        // Exhaust the Fixed Window (STRING) state for this tenant/resource/IP.
        adapter.evaluate(fixedWindowKey, quota).block();
        adapter.evaluate(fixedWindowKey, quota).block();
        adapter.evaluate(fixedWindowKey, quota).block();
        RateLimitDecision exhausted = adapter.evaluate(fixedWindowKey, quota).block();
        assertThat(exhausted.allowed()).isFalse();

        // Switching to a ZSET-backed strategy for the same tenant/resource/IP must not error and
        // must start from fresh capacity, not "inherit" the exhausted Fixed Window state.
        assertThatCode(() -> {
            RateLimitDecision decision = adapter.evaluate(slidingLogKey, quota).block();
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(2);
        }).doesNotThrowAnyException();

        // A HASH-backed strategy (Sliding Window Counter) likewise starts fresh, no WRONGTYPE.
        assertThatCode(() -> {
            RateLimitDecision decision = adapter.evaluate(hashBackedKey, quota).block();
            assertThat(decision.allowed()).isTrue();
        }).doesNotThrowAnyException();

        // And a different STRING-backed strategy (Leaky Bucket) does not collide with Fixed
        // Window's own STRING key, since the strategy is embedded in the key name.
        assertThatCode(() -> {
            RateLimitDecision decision = adapter.evaluate(stringBackedKey, quota).block();
            assertThat(decision.allowed()).isTrue();
        }).doesNotThrowAnyException();
    }
}
