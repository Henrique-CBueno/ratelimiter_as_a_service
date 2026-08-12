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

class LeakyBucketRedisAdapterIT extends AbstractRedisIT {

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);

    // limit=5 per 5s window => emission interval 1s; burst capacity 5 => tau = (5-1)*1s = 4s.
    private static final Quota QUOTA = Quota.withBurst(5, Duration.ofSeconds(5), 5);

    private static RateLimitKey newKey() {
        return new RateLimitKey(TenantId.generate(), ResourceId.generate(), new ClientIp("203.0.113.50"),
                StrategyType.LEAKY_BUCKET);
    }

    private static void seedTat(String redisKey, long tatMs) {
        REDIS_TEMPLATE.opsForValue().set(redisKey, String.valueOf(tatMs)).block();
    }

    @Test
    void firstRequestEverIsAllowedFromAnEmptyBucket() {
        RateLimitDecision decision = adapter.evaluate(newKey(), QUOTA).block();

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void requestAllowedAtOrAfterTheAllowedBoundary() {
        RateLimitKey key = newKey();
        long tat = fetchRedisNowMs() + 4000; // exactly tau ahead -> allowAt == seed time
        seedTat(RedisKeyBuilder.build(key), tat);

        RateLimitDecision decision = adapter.evaluate(key, QUOTA).block();

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void requestDeniedWhenItArrivesBeforeTheAllowedTime() {
        RateLimitKey key = newKey();
        long tat = fetchRedisNowMs() + 6000; // 6s ahead, beyond the 4s tau -> must wait ~2s
        seedTat(RedisKeyBuilder.build(key), tat);

        RateLimitDecision decision = adapter.evaluate(key, QUOTA).block();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(500));
    }

    @Test
    void deniedRequestLeavesStoredTatUnchanged() {
        RateLimitKey key = newKey();
        String redisKey = RedisKeyBuilder.build(key);
        long tat = fetchRedisNowMs() + 6000;
        seedTat(redisKey, tat);

        adapter.evaluate(key, QUOTA).block();

        String storedAfter = REDIS_TEMPLATE.opsForValue().get(redisKey).block();
        assertThat(Double.parseDouble(storedAfter)).isEqualTo((double) tat);
    }
}
