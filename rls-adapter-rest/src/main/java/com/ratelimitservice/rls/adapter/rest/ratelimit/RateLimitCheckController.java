package com.ratelimitservice.rls.adapter.rest.ratelimit;

import com.ratelimitservice.rls.adapter.rest.auth.AuthenticatedTenant;
import com.ratelimitservice.rls.adapter.rest.error.ResourceNotFoundException;
import com.ratelimitservice.rls.application.ratelimit.CheckRateLimitUseCase;
import com.ratelimitservice.rls.domain.ratelimit.RateLimitDecision;
import com.ratelimitservice.rls.domain.shared.ClientIp;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ratelimit")
public class RateLimitCheckController {

    private final CheckRateLimitUseCase checkRateLimitUseCase;

    public RateLimitCheckController(CheckRateLimitUseCase checkRateLimitUseCase) {
        this.checkRateLimitUseCase = checkRateLimitUseCase;
    }

    @PostMapping("/check")
    public Mono<ResponseEntity<CheckResponse>> check(ServerWebExchange exchange, @RequestBody CheckRequest request) {
        Tenant tenant = exchange.getAttribute(AuthenticatedTenant.ATTRIBUTE);
        ClientIp clientIp = new ClientIp(request.clientIp());

        return checkRateLimitUseCase.check(tenant, request.resource(), clientIp)
                .map(this::toResponseEntity)
                .switchIfEmpty(Mono.error(ResourceNotFoundException.forKey(request.resource())));
    }

    private ResponseEntity<CheckResponse> toResponseEntity(CheckRateLimitUseCase.CheckResult result) {
        RateLimitDecision decision = result.decision();
        HttpStatus status = decision.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header("RateLimit-Limit", String.valueOf(decision.limit()))
                .header("RateLimit-Remaining", String.valueOf(decision.remaining()))
                .header("RateLimit-Reset", String.valueOf(decision.resetAt().getEpochSecond()));

        Long retryAfterSeconds = null;
        if (decision.retryAfter() != null) {
            retryAfterSeconds = decision.retryAfter().toSeconds();
            builder = builder.header("Retry-After", String.valueOf(retryAfterSeconds));
        }

        CheckResponse body = new CheckResponse(decision.allowed(), decision.limit(), decision.remaining(),
                decision.resetAt(), retryAfterSeconds, result.strategyType());
        return builder.body(body);
    }
}
