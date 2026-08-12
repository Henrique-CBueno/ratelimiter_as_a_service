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

class SlidingWindowCounterRedisAdapterIT extends AbstractRedisIT {

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);

    private static RateLimitKey newKey() {
        return new RateLimitKey(TenantId.generate(), ResourceId.generate(), new ClientIp("203.0.113.30"),
                StrategyType.SLIDING_WINDOW_COUNTER);
    }

    private static void seed(String redisKey, int prev, int curr, long windowStartMs) {
        REDIS_TEMPLATE.opsForHash().putAll(redisKey, Map.of(
                "prev", String.valueOf(prev),
                "curr", String.valueOf(curr),
                "window_start_ms", String.valueOf(windowStartMs)
        )).block();
    }

    @Test
    void allowsRequestsUnderTheWeightedEstimate() {
        RateLimitKey key = newKey();
        // A 10-minute window means the few hundred milliseconds of latency between seeding and
        // evaluating are a negligible fraction of it, so the elapsed-fraction-derived remaining
        // count below stays exact instead of flaking on real timing jitter.
        Quota quota = Quota.of(10, Duration.ofMinutes(10));
        long windowStartMs = fetchRedisNowMs() - 300_000; // halfway through the window
        seed(RedisKeyBuilder.build(key), 8, 0, windowStartMs);

        // estimate before this request = 8*0.5 + 0 = 4 < 10 -> allowed
        RateLimitDecision decision = adapter.evaluate(key, quota).block();

        assertThat(decision.allowed()).isTrue();
        // estimate after = 8*0.5 + 1 = 5 -> remaining = 10 - 5 = 5
        assertThat(decision.remaining()).isEqualTo(5);
    }

    @Test
    void deniesRequestsWhenWeightedEstimateReachesLimit() {
        RateLimitKey key = newKey();
        Quota quota = Quota.of(10, Duration.ofSeconds(60));
        long windowStartMs = fetchRedisNowMs() - 30_000; // halfway through the window
        // estimate = 10*0.5 + 8 = 13, comfortably >= 10 regardless of a few ms of test latency
        // (unlike sitting exactly at the boundary, which real elapsed time could tip either way)
        seed(RedisKeyBuilder.build(key), 10, 8, windowStartMs);

        RateLimitDecision decision = adapter.evaluate(key, quota).block();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isNotNull();
    }

    @Test
    void windowRolloverShiftsCurrentIntoPrevious() {
        RateLimitKey key = newKey();
        Quota quota = Quota.of(10, Duration.ofSeconds(60));
        long windowStartMs = fetchRedisNowMs() - 65_000; // just over one window elapsed
        seed(RedisKeyBuilder.build(key), 3, 7, windowStartMs);

        adapter.evaluate(key, quota).block();

        Map<Object, Object> state = REDIS_TEMPLATE.opsForHash()
                .entries(RedisKeyBuilder.build(key))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .block();
        assertThat(state.get("prev")).isEqualTo("7");
    }
}
