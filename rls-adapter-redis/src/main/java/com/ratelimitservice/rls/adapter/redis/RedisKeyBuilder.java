package com.ratelimitservice.rls.adapter.redis;

import com.ratelimitservice.rls.domain.ratelimit.RateLimitKey;

public final class RedisKeyBuilder {

    private RedisKeyBuilder() {
    }

    public static String build(RateLimitKey key) {
        return "rl:" + key.tenantId().value() + ":" + key.resourceId().value() + ":"
                + key.clientIp().value() + ":" + key.strategyType().name();
    }
}
