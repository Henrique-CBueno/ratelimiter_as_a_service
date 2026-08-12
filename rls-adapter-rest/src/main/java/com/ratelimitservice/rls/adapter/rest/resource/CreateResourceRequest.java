package com.ratelimitservice.rls.adapter.rest.resource;

import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;

public record CreateResourceRequest(String resourceKey, StrategyType strategyType, int limit,
                                     int windowSeconds, Integer burstCapacity, FallbackPolicy fallbackPolicy) {
}
