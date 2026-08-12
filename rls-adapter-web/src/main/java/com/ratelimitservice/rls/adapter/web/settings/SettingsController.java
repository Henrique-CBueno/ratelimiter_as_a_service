package com.ratelimitservice.rls.adapter.web.settings;

import com.ratelimitservice.rls.adapter.web.auth.TenantPrincipal;
import com.ratelimitservice.rls.application.tenant.GetTenantUseCase;
import com.ratelimitservice.rls.application.tenant.RotateApiTokenUseCase;
import com.ratelimitservice.rls.domain.tenant.ApiToken;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

@org.springframework.stereotype.Controller
public class SettingsController {

    private final GetTenantUseCase getTenantUseCase;
    private final RotateApiTokenUseCase rotateApiTokenUseCase;

    public SettingsController(GetTenantUseCase getTenantUseCase, RotateApiTokenUseCase rotateApiTokenUseCase) {
        this.getTenantUseCase = getTenantUseCase;
        this.rotateApiTokenUseCase = rotateApiTokenUseCase;
    }

    @GetMapping("/app/settings")
    public Mono<Rendering> settings(@AuthenticationPrincipal TenantPrincipal principal) {
        return getTenantUseCase.get(principal.tenantId())
                .map(tenant -> Rendering.view("settings")
                        .modelAttribute("tokenPrefix", activeTokenPrefix(tenant))
                        .build());
    }

    @PostMapping("/app/settings/rotate")
    public Mono<Rendering> rotate(@AuthenticationPrincipal TenantPrincipal principal) {
        return getTenantUseCase.get(principal.tenantId())
                .flatMap(rotateApiTokenUseCase::rotate)
                .map(rawToken -> Rendering.view("settings")
                        .modelAttribute("rotatedToken", rawToken)
                        .build());
    }

    private String activeTokenPrefix(Tenant tenant) {
        return tenant.apiTokens().stream()
                .filter(ApiToken::isActive)
                .findFirst()
                .map(ApiToken::tokenPrefix)
                .orElse("");
    }
}
