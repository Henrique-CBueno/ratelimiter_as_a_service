package com.ratelimitservice.rls.adapter.redis;

import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RedisRateLimitEvaluationAdapter implements RateLimitEvaluationPort {

    private static final Map<StrategyType, String> SCRIPT_RESOURCES = Map.of(
            StrategyType.FIXED_WINDOW, "scripts/fixed_window.lua",
            StrategyType.SLIDING_WINDOW_LOG, "scripts/sliding_window_log.lua",
            StrategyType.SLIDING_WINDOW_COUNTER, "scripts/sliding_window_counter.lua"
    );

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Map<StrategyType, RedisScript<String>> scriptsByType;

    public RedisRateLimitEvaluationAdapter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.scriptsByType = new EnumMap<>(StrategyType.class);
        SCRIPT_RESOURCES.forEach((type, resource) -> scriptsByType.put(type, loadScript(resource)));
    }

    private static RedisScript<String> loadScript(String classpathLocation) {
        return RedisScript.of(new ClassPathResource(classpathLocation), String.class);
    }

    @Override
    public Mono<RateLimitDecision> evaluate(RateLimitKey key, Quota quota) {
        RedisScript<String> script = scriptsByType.get(key.strategyType());
        if (script == null) {
            return Mono.error(new IllegalArgumentException(
                    "No Redis script registered for strategy: " + key.strategyType()));
        }

        String redisKey = RedisKeyBuilder.build(key);
        List<String> args = List.of(
                String.valueOf(quota.limit()),
                String.valueOf(quota.window().toMillis()),
                String.valueOf(quota.effectiveBurstCapacity())
        );

        return redisTemplate.execute(script, List.of(redisKey), args)
                .single()
                .map(RedisRateLimitEvaluationAdapter::toDecision);
    }

    private static RateLimitDecision toDecision(String reply) {
        String[] parts = reply.split(",");
        boolean allowed = "1".equals(parts[0]);
        int limit = Integer.parseInt(parts[1]);
        int remaining = Integer.parseInt(parts[2]);
        Instant resetAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));

        if (allowed) {
            return RateLimitDecision.allow(limit, remaining, resetAt);
        }
        long retryAfterMs = Long.parseLong(parts[4]);
        return RateLimitDecision.deny(limit, resetAt, Duration.ofMillis(retryAfterMs));
    }
}
