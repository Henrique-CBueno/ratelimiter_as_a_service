package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.port.SecretHasherPort;
import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

public final class LoginTenantUseCase {

    private final TenantRepositoryPort tenantRepositoryPort;
    private final SecretHasherPort secretHasherPort;

    public LoginTenantUseCase(TenantRepositoryPort tenantRepositoryPort, SecretHasherPort secretHasherPort) {
        this.tenantRepositoryPort = tenantRepositoryPort;
        this.secretHasherPort = secretHasherPort;
    }

    public Mono<Tenant> login(String email, String rawPassword) {
        return tenantRepositoryPort.findByEmail(email)
                .filter(tenant -> secretHasherPort.matches(rawPassword, tenant.passwordHash()));
    }
}
