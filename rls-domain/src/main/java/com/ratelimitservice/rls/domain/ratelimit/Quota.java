package com.ratelimitservice.rls.domain.ratelimit;

import java.time.Duration;

public record Quota(int limit, Duration window, Integer burstCapacity) {

    public Quota {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be a positive duration");
        }
        if (burstCapacity != null && burstCapacity <= 0) {
            throw new IllegalArgumentException("burstCapacity must be positive when provided");
        }
    }

    public static Quota of(int limit, Duration window) {
        return new Quota(limit, window, null);
    }

    public static Quota withBurst(int limit, Duration window, int burstCapacity) {
        return new Quota(limit, window, burstCapacity);
    }

    public int effectiveBurstCapacity() {
        return burstCapacity != null ? burstCapacity : limit;
    }
}
