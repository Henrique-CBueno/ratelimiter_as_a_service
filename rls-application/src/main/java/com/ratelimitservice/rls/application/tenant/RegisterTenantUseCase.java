package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.security.port.SecretHasherPort;
import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

import java.time.Instant;

public final class RegisterTenantUseCase {

    private final TenantRepositoryPort tenantRepositoryPort;
    private final SecretHasherPort secretHasherPort;

    public RegisterTenantUseCase(TenantRepositoryPort tenantRepositoryPort, SecretHasherPort secretHasherPort) {
        this.tenantRepositoryPort = tenantRepositoryPort;
        this.secretHasherPort = secretHasherPort;
    }

    public Mono<Result> register(String name, String email, String rawPassword, FallbackPolicy defaultFallbackPolicy) {
        String passwordHash = secretHasherPort.hash(rawPassword);
        Tenant tenant = Tenant.register(name, email, passwordHash, defaultFallbackPolicy);

        String rawToken = ApiTokenGenerator.generate();
        tenant.issueApiToken(ApiTokenGenerator.fingerprint(rawToken), ApiTokenGenerator.displayPrefix(rawToken), Instant.now());

        return tenantRepositoryPort.save(tenant)
                .map(saved -> new Result(saved.id(), rawToken));
    }

    public record Result(TenantId tenantId, String apiToken) {
    }
}
