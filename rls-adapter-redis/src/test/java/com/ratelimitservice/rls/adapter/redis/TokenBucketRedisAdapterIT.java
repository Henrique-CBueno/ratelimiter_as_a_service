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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRedisAdapterIT extends AbstractRedisIT {

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);

    private static RateLimitKey newKey() {
        return new RateLimitKey(TenantId.generate(), ResourceId.generate(), new ClientIp("203.0.113.40"),
                StrategyType.TOKEN_BUCKET);
    }

    private static void seed(String redisKey, double tokens, long lastRefillMs) {
        REDIS_TEMPLATE.opsForHash().putAll(redisKey, Map.of(
                "tokens", String.valueOf(tokens),
                "last_refill_ms", String.valueOf(lastRefillMs)
        )).block();
    }

    @Test
    void firstRequestEverStartsFromAFullBucketAndIsAllowed() {
        RateLimitKey key = newKey();
        Quota quota = Quota.withBurst(5, Duration.ofSeconds(5), 5);

        RateLimitDecision decision = adapter.evaluate(key, quota).block();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(4);
    }

    @Test
    void requestAllowedWhenTokensAvailableAfterRefill() {
        RateLimitKey key = newKey();
        // 5 tokens per 5s => 1 token/second refill rate
        Quota quota = Quota.withBurst(5, Duration.ofSeconds(5), 5);
        long lastRefillMs = fetchRedisNowMs() - 3000; // 3s ago -> ~3 tokens refilled
        seed(RedisKeyBuilder.build(key), 0.0, lastRefillMs);

        RateLimitDecision decision = adapter.evaluate(key, quota).block();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(2);
    }

    @Test
    void requestDeniedWhenNoTokensAvailable() {
        RateLimitKey key = newKey();
        Quota quota = Quota.withBurst(5, Duration.ofSeconds(5), 5);
        long lastRefillMs = fetchRedisNowMs();
        seed(RedisKeyBuilder.build(key), 0.0, lastRefillMs);

        RateLimitDecision decision = adapter.evaluate(key, quota).block();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isNotNull();
    }

    @Test
    void refillNeverExceedsCapacity() {
        RateLimitKey key = newKey();
        Quota quota = Quota.withBurst(5, Duration.ofSeconds(5), 5);
        long lastRefillMs = fetchRedisNowMs() - 100_000; // long enough ago to way overflow capacity
        seed(RedisKeyBuilder.build(key), 5.0, lastRefillMs);

        RateLimitDecision decision = adapter.evaluate(key, quota).block();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(4);
    }
}
