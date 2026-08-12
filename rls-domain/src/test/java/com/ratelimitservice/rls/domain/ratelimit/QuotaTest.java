package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaTest {

    @Test
    void createsQuotaWithoutBurstCapacity() {
        Quota quota = Quota.of(100, Duration.ofMinutes(1));

        assertThat(quota.limit()).isEqualTo(100);
        assertThat(quota.window()).isEqualTo(Duration.ofMinutes(1));
        assertThat(quota.burstCapacity()).isNull();
    }

    @Test
    void createsQuotaWithBurstCapacity() {
        Quota quota = Quota.withBurst(100, Duration.ofMinutes(1), 150);

        assertThat(quota.burstCapacity()).isEqualTo(150);
    }

    @Test
    void effectiveBurstCapacityFallsBackToLimitWhenUnset() {
        Quota quota = Quota.of(100, Duration.ofMinutes(1));

        assertThat(quota.effectiveBurstCapacity()).isEqualTo(100);
    }

    @Test
    void effectiveBurstCapacityUsesExplicitValueWhenSet() {
        Quota quota = Quota.withBurst(100, Duration.ofMinutes(1), 150);

        assertThat(quota.effectiveBurstCapacity()).isEqualTo(150);
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> Quota.of(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quota.of(-1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThatThrownBy(() -> Quota.of(100, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quota.of(100, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullWindow() {
        assertThatThrownBy(() -> Quota.of(100, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveBurstCapacityWhenProvided() {
        assertThatThrownBy(() -> Quota.withBurst(100, Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
