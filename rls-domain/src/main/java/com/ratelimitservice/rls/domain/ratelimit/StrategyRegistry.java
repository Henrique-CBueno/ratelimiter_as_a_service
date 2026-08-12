package com.ratelimitservice.rls.domain.ratelimit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class StrategyRegistry {

    private final Map<StrategyType, RateLimitStrategy> strategiesByType = new EnumMap<>(StrategyType.class);

    public StrategyRegistry() {
        register(new FixedWindowStrategy());
        register(new SlidingWindowLogStrategy());
        register(new SlidingWindowCounterStrategy());
        register(new TokenBucketStrategy());
        register(new LeakyBucketStrategy());
    }

    private void register(RateLimitStrategy strategy) {
        strategiesByType.put(strategy.type(), strategy);
    }

    public RateLimitStrategy resolve(StrategyType strategyType) {
        Objects.requireNonNull(strategyType, "strategyType must not be null");
        RateLimitStrategy strategy = strategiesByType.get(strategyType);
        if (strategy == null) {
            throw new IllegalArgumentException("No RateLimitStrategy registered for type: " + strategyType);
        }
        return strategy;
    }
}
