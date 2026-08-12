package com.ratelimitservice.rls.application.tenant.port;

import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

public interface TenantRepositoryPort {

    Mono<Tenant> save(Tenant tenant);

    Mono<Tenant> findById(TenantId id);

    Mono<Tenant> findByEmail(String email);

    Mono<Tenant> findByActiveTokenHash(String tokenHash);
}
