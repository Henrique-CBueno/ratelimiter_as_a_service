package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryTest {

    private final StrategyRegistry registry = new StrategyRegistry();

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void resolvesEveryKnownStrategyType(StrategyType strategyType) {
        RateLimitStrategy strategy = registry.resolve(strategyType);

        assertThat(strategy).isNotNull();
        assertThat(strategy.type()).isEqualTo(strategyType);
    }

    @Test
    void rejectsNullStrategyType() {
        // StrategyType is a closed enum covering all five supported strategies, so there is no
        // constructible "unknown" value; a null input exercises the same rejection path that
        // would trigger if a type were ever left unregistered.
        assertThatThrownBy(() -> registry.resolve(null))
                .isInstanceOf(NullPointerException.class);
    }
}
