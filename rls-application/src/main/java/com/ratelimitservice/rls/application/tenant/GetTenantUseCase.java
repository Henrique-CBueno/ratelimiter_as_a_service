package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

public final class GetTenantUseCase {

    private final TenantRepositoryPort tenantRepositoryPort;

    public GetTenantUseCase(TenantRepositoryPort tenantRepositoryPort) {
        this.tenantRepositoryPort = tenantRepositoryPort;
    }

    public Mono<Tenant> get(TenantId tenantId) {
        return tenantRepositoryPort.findById(tenantId);
    }
}
