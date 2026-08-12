package com.ratelimitservice.rls.adapter.web.resource;

import com.ratelimitservice.rls.domain.ratelimit.StrategyType;

/**
 * Backs the {@code POST /app/resources/{id}} form (see {@link CreateResourceForm} for why form
 * posts need a {@code @ModelAttribute} bean rather than {@code @RequestParam}). No
 * {@code resourceKey} field: like the REST API's {@code UpdateResourceRequest}, editing only
 * reconfigures strategy and quota.
 */
public class EditResourceForm {

    private StrategyType strategyType;
    private int limit;
    private int windowSeconds;
    private Integer burstCapacity;

    public StrategyType getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(StrategyType strategyType) {
        this.strategyType = strategyType;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Integer getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(Integer burstCapacity) {
        this.burstCapacity = burstCapacity;
    }
}
