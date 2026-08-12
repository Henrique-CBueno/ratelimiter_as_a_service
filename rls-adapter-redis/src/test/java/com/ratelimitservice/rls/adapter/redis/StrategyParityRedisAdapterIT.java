package com.ratelimitservice.rls.adapter.redis;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitEvaluation;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitState;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitStrategy;
import com.ratelimitservice.rls.domain.ratelimit.StrategyRegistry;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives an identical deterministic sequence of evaluations through both the Redis/Lua adapter
 * and the corresponding pure {@link RateLimitStrategy} from rls-domain (spec 1), asserting they
 * agree on every step. The Lua scripts always source "now" from Redis itself (design decision:
 * Redis is the authoritative clock), so each step reads Redis's current time first and feeds that
 * same instant to the domain side — the two clock reads are a network round-trip apart, which the
 * 10-second window here makes negligible.
 *
 * <p>For LEAKY_BUCKET specifically, this sequence exercises exactly the burst-boundary case that
 * would catch a regression in the GCRA {@code tau} formula: whether the 6th rapid-fire request in
 * a 5-capacity burst is correctly denied depends entirely on {@code tau} being
 * {@code (burstCapacity - 1) * emissionInterval} and not {@code burstCapacity * emissionInterval}.
 */
class StrategyParityRedisAdapterIT extends AbstractRedisIT {

    private static final int LIMIT = 5;
    private static final int STEPS = LIMIT + 2;

    private final RedisRateLimitEvaluationAdapter adapter = new RedisRateLimitEvaluationAdapter(REDIS_TEMPLATE);
    private final StrategyRegistry domainStrategies = new StrategyRegistry();

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void redisScriptMatchesDomainStrategyForAnIdenticalCallSequence(StrategyType strategyType) {
        RateLimitStrategy domainStrategy = domainStrategies.resolve(strategyType);
        RateLimitKey key = new RateLimitKey(TenantId.generate(), ResourceId.generate(),
                new ClientIp("203.0.113.90"), strategyType);
        Quota quota = Quota.withBurst(LIMIT, Duration.ofSeconds(10), LIMIT);

        RateLimitState domainState = null;

        for (int step = 1; step <= STEPS; step++) {
            long nowMs = fetchRedisNowMs();
            Instant now = Instant.ofEpochMilli(nowMs);

            RateLimitEvaluation domainEvaluation = domainStrategy.evaluate(domainState, quota, now);
            domainState = domainEvaluation.newState();
            RateLimitDecision domainDecision = domainEvaluation.decision();

            RateLimitDecision redisDecision = adapter.evaluate(key, quota).block();

            assertThat(redisDecision.allowed())
                    .as("step %d: allowed mismatch between Redis and domain for %s", step, strategyType)
                    .isEqualTo(domainDecision.allowed());
            assertThat(redisDecision.remaining())
                    .as("step %d: remaining mismatch between Redis and domain for %s", step, strategyType)
                    .isEqualTo(domainDecision.remaining());
        }
    }
}
