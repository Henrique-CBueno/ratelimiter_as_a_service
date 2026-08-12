package com.ratelimitservice.rls.adapter.rest.tenant;

import com.ratelimitservice.rls.adapter.rest.auth.AuthenticatedTenant;
import com.ratelimitservice.rls.application.tenant.RegisterTenantUseCase;
import com.ratelimitservice.rls.application.tenant.RotateApiTokenUseCase;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class TenantController {

    private final RegisterTenantUseCase registerTenantUseCase;
    private final RotateApiTokenUseCase rotateApiTokenUseCase;

    public TenantController(RegisterTenantUseCase registerTenantUseCase, RotateApiTokenUseCase rotateApiTokenUseCase) {
        this.registerTenantUseCase = registerTenantUseCase;
        this.rotateApiTokenUseCase = rotateApiTokenUseCase;
    }

    @PostMapping("/api/v1/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterTenantResponse> register(@RequestBody RegisterTenantRequest request) {
        return registerTenantUseCase
                .register(request.name(), request.email(), request.password(), FallbackPolicy.FAIL_OPEN)
                .map(result -> new RegisterTenantResponse(result.tenantId().value(), result.apiToken()));
    }

    @PostMapping("/api/v1/tokens/rotate")
    public Mono<RotateTokenResponse> rotate(ServerWebExchange exchange) {
        Tenant tenant = exchange.getAttribute(AuthenticatedTenant.ATTRIBUTE);
        return rotateApiTokenUseCase.rotate(tenant).map(RotateTokenResponse::new);
    }
}
