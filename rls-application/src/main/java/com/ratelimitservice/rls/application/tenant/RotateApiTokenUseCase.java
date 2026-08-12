package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

import java.time.Instant;

public final class RotateApiTokenUseCase {

    private final TenantRepositoryPort tenantRepositoryPort;

    public RotateApiTokenUseCase(TenantRepositoryPort tenantRepositoryPort) {
        this.tenantRepositoryPort = tenantRepositoryPort;
    }

    public Mono<String> rotate(Tenant tenant) {
        String rawToken = ApiTokenGenerator.generate();
        tenant.rotateApiToken(ApiTokenGenerator.fingerprint(rawToken), ApiTokenGenerator.displayPrefix(rawToken), Instant.now());
        return tenantRepositoryPort.save(tenant).thenReturn(rawToken);
    }
}
