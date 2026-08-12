package com.ratelimitservice.rls.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitDecisionTest {

    @Test
    void allowFactoryCreatesNonDegradedAllowedDecisionWithoutRetryAfter() {
        Instant resetAt = Instant.parse("2026-01-01T00:00:00Z");

        RateLimitDecision decision = RateLimitDecision.allow(10, 4, resetAt);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(10);
        assertThat(decision.remaining()).isEqualTo(4);
        assertThat(decision.resetAt()).isEqualTo(resetAt);
        assertThat(decision.retryAfter()).isNull();
        assertThat(decision.degraded()).isFalse();
    }

    @Test
    void denyFactoryCreatesDecisionWithRetryAfterAndZeroRemaining() {
        Instant resetAt = Instant.parse("2026-01-01T00:00:00Z");

        RateLimitDecision decision = RateLimitDecision.deny(10, resetAt, Duration.ofSeconds(30));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void deniedDecisionRequiresNonNullRetryAfter() {
        assertThatThrownBy(() -> new RateLimitDecision(false, 10, 0, Instant.now(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeLimitOrRemaining() {
        assertThatThrownBy(() -> new RateLimitDecision(true, -1, 0, Instant.now(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitDecision(true, 10, -1, Instant.now(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asDegradedPreservesFieldsButMarksDegradedTrue() {
        RateLimitDecision decision = RateLimitDecision.allow(10, 4, Instant.parse("2026-01-01T00:00:00Z"));

        RateLimitDecision degraded = decision.asDegraded();

        assertThat(degraded.degraded()).isTrue();
        assertThat(degraded.allowed()).isEqualTo(decision.allowed());
        assertThat(degraded.limit()).isEqualTo(decision.limit());
        assertThat(degraded.remaining()).isEqualTo(decision.remaining());
        assertThat(degraded.resetAt()).isEqualTo(decision.resetAt());
    }
}
