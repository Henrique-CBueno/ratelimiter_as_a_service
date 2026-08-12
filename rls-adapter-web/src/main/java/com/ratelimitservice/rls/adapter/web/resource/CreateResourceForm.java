package com.ratelimitservice.rls.adapter.web.resource;

import com.ratelimitservice.rls.domain.ratelimit.StrategyType;

/**
 * Backs the {@code POST /app/resources} form (see {@link com.ratelimitservice.rls.adapter.web.onboarding.RegisterForm}
 * for why form posts need a {@code @ModelAttribute} bean rather than {@code @RequestParam}).
 */
public class CreateResourceForm {

    private String resourceKey;
    private StrategyType strategyType;
    private int limit;
    private int windowSeconds;
    private Integer burstCapacity;

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }

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
