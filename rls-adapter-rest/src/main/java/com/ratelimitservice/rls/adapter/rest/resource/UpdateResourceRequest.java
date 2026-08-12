package com.ratelimitservice.rls.adapter.rest.resource;

import com.ratelimitservice.rls.domain.ratelimit.StrategyType;

public record UpdateResourceRequest(StrategyType strategyType, int limit, int windowSeconds, Integer burstCapacity) {
}
