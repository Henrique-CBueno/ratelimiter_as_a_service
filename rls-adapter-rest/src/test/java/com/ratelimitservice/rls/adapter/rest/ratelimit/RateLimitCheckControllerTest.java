package com.ratelimitservice.rls.adapter.rest.ratelimit;

import com.ratelimitservice.rls.adapter.rest.auth.FakeAuthenticatedTenantFilterConfig;
import com.ratelimitservice.rls.adapter.rest.error.RestExceptionHandler;
import com.ratelimitservice.rls.application.ratelimit.CheckRateLimitUseCase;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(RateLimitCheckController.class)
@Import({RestExceptionHandler.class, FakeAuthenticatedTenantFilterConfig.class})
class RateLimitCheckControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CheckRateLimitUseCase checkRateLimitUseCase;

    @Test
    void allowedCheckReturnsOkWithRateLimitHeaders() {
        RateLimitDecision decision = RateLimitDecision.allow(10, 9, Instant.parse("2026-01-01T00:00:00Z"));
        when(checkRateLimitUseCase.check(any(), any(), any()))
                .thenReturn(Mono.just(new CheckRateLimitUseCase.CheckResult(decision, StrategyType.FIXED_WINDOW)));

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .bodyValue(new CheckRequest("/login", "203.0.113.1"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("RateLimit-Limit", "10")
                .expectHeader().valueEquals("RateLimit-Remaining", "9")
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(true)
                .jsonPath("$.strategy").isEqualTo("FIXED_WINDOW");
    }

    @Test
    void deniedCheckReturnsTooManyRequestsWithRetryAfter() {
        RateLimitDecision decision = RateLimitDecision.deny(10, Instant.parse("2026-01-01T00:01:00Z"), Duration.ofSeconds(30));
        when(checkRateLimitUseCase.check(any(), any(), any()))
                .thenReturn(Mono.just(new CheckRateLimitUseCase.CheckResult(decision, StrategyType.FIXED_WINDOW)));

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .bodyValue(new CheckRequest("/login", "203.0.113.1"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(false)
                .jsonPath("$.retryAfterSeconds").isEqualTo(30);
    }

    @Test
    void checkingAnUnconfiguredResourceReturnsNotFound() {
        when(checkRateLimitUseCase.check(any(), any(), any())).thenReturn(Mono.empty());

        webTestClient.post().uri("/api/v1/ratelimit/check")
                .bodyValue(new CheckRequest("/unknown", "203.0.113.1"))
                .exchange()
                .expectStatus().isNotFound();
    }
}
