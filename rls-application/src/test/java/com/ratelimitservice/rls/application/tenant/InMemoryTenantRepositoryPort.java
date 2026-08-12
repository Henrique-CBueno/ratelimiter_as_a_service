package com.ratelimitservice.rls.application.tenant;

import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryTenantRepositoryPort implements TenantRepositoryPort {

    private final Map<TenantId, Tenant> byId = new LinkedHashMap<>();

    @Override
    public Mono<Tenant> save(Tenant tenant) {
        boolean emailTakenByAnother = byId.values().stream()
                .anyMatch(existing -> !existing.id().equals(tenant.id()) && existing.email().equals(tenant.email()));
        if (emailTakenByAnother) {
            return Mono.error(new DuplicateEmailException(tenant.email()));
        }
        byId.put(tenant.id(), tenant);
        return Mono.just(tenant);
    }

    @Override
    public Mono<Tenant> findById(TenantId id) {
        Tenant tenant = byId.get(id);
        return tenant != null ? Mono.just(tenant) : Mono.empty();
    }

    @Override
    public Mono<Tenant> findByEmail(String email) {
        return byId.values().stream()
                .filter(tenant -> tenant.email().equals(email))
                .findFirst()
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }

    @Override
    public Mono<Tenant> findByActiveTokenHash(String tokenHash) {
        return byId.values().stream()
                .filter(tenant -> tenant.apiTokens().stream()
                        .anyMatch(token -> token.isActive() && token.tokenHash().equals(tokenHash)))
                .findFirst()
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }
}
