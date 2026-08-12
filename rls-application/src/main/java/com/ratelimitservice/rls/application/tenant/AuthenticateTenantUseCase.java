package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

public final class AuthenticateTenantUseCase {

    private final TenantRepositoryPort tenantRepositoryPort;

    public AuthenticateTenantUseCase(TenantRepositoryPort tenantRepositoryPort) {
        this.tenantRepositoryPort = tenantRepositoryPort;
    }

    public Mono<Tenant> authenticate(String rawToken) {
        return tenantRepositoryPort.findByActiveTokenHash(ApiTokenGenerator.fingerprint(rawToken));
    }
}
