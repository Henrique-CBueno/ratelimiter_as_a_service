package com.ratelimitservice.rls.adapter.persistence.resource;

import com.ratelimitservice.rls.adapter.persistence.AbstractPostgresIT;
import com.ratelimitservice.rls.adapter.persistence.tenant.TenantRepositoryAdapter;
import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceRepositoryAdapterIT extends AbstractPostgresIT {

    private final ResourceRepositoryAdapter repository = new ResourceRepositoryAdapter(DATABASE_CLIENT);
    private final TenantRepositoryAdapter tenantRepository = new TenantRepositoryAdapter(DATABASE_CLIENT);

    private TenantId newSavedTenant(String email) {
        Tenant tenant = Tenant.register("Acme Inc", email, "hashed-secret", FallbackPolicy.FAIL_OPEN);
        tenantRepository.save(tenant).block();
        return tenant.id();
    }

    @Test
    void savedResourceIsRetrievableByIdWithIdenticalState() {
        TenantId tenantId = newSavedTenant("owner1@acme.test");
        RateLimitResource resource = RateLimitResource.create(tenantId, "/login", StrategyType.TOKEN_BUCKET,
                Quota.withBurst(5, Duration.ofSeconds(10), 5));

        repository.save(resource).block();
        RateLimitResource reloaded = repository.findById(resource.id()).block();

        assertThat(reloaded.id()).isEqualTo(resource.id());
        assertThat(reloaded.tenantId()).isEqualTo(tenantId);
        assertThat(reloaded.resourceKey()).isEqualTo("/login");
        assertThat(reloaded.strategyType()).isEqualTo(StrategyType.TOKEN_BUCKET);
        assertThat(reloaded.quota()).isEqualTo(resource.quota());
        assertThat(reloaded.enabled()).isTrue();
    }

    @Test
    void findByTenantAndKeyFindsAnExistingResource() {
        TenantId tenantId = newSavedTenant("owner2@acme.test");
        RateLimitResource resource = RateLimitResource.create(tenantId, "/search", StrategyType.FIXED_WINDOW,
                Quota.of(100, Duration.ofMinutes(1)));
        repository.save(resource).block();

        RateLimitResource found = repository.findByTenantAndKey(tenantId, "/search").block();

        assertThat(found.id()).isEqualTo(resource.id());
    }

    @Test
    void findByTenantAndKeyReturnsEmptyForANonExistentResource() {
        TenantId tenantId = newSavedTenant("owner3@acme.test");

        RateLimitResource found = repository.findByTenantAndKey(tenantId, "/does-not-exist").block();

        assertThat(found).isNull();
    }

    @Test
    void findAllByTenantReturnsOnlyThatTenantsResources() {
        TenantId tenantA = newSavedTenant("tenantA@acme.test");
        TenantId tenantB = newSavedTenant("tenantB@acme.test");
        repository.save(RateLimitResource.create(tenantA, "/a1", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
        repository.save(RateLimitResource.create(tenantA, "/a2", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
        repository.save(RateLimitResource.create(tenantB, "/b1", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();

        var tenantAResources = repository.findAllByTenant(tenantA).collectList().block();

        assertThat(tenantAResources).hasSize(2);
        assertThat(tenantAResources).allMatch(r -> r.tenantId().equals(tenantA));
    }

    @Test
    void duplicateResourceKeyForTheSameTenantIsRejected() {
        TenantId tenantId = newSavedTenant("dup-owner@acme.test");
        repository.save(RateLimitResource.create(tenantId, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
        RateLimitResource second = RateLimitResource.create(tenantId, "/login", StrategyType.TOKEN_BUCKET,
                Quota.withBurst(5, Duration.ofSeconds(10), 5));

        assertThatThrownBy(() -> repository.save(second).block())
                .isInstanceOf(DuplicateResourceKeyException.class);
    }

    @Test
    void theSameResourceKeyIsAllowedForDifferentTenants() {
        TenantId tenantA = newSavedTenant("same-key-a@acme.test");
        TenantId tenantB = newSavedTenant("same-key-b@acme.test");

        assertThatCode(() -> {
            repository.save(RateLimitResource.create(tenantA, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
            repository.save(RateLimitResource.create(tenantB, "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)))).block();
        }).doesNotThrowAnyException();
    }

    @Test
    void reconfiguredStrategyAndQuotaArePersisted() {
        TenantId tenantId = newSavedTenant("reconfig@acme.test");
        RateLimitResource resource = RateLimitResource.create(tenantId, "/login", StrategyType.FIXED_WINDOW,
                Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        Quota newQuota = Quota.withBurst(3, Duration.ofSeconds(30), 3);
        resource.reconfigure(StrategyType.TOKEN_BUCKET, newQuota);
        repository.save(resource).block();

        RateLimitResource reloaded = repository.findById(resource.id()).block();
        assertThat(reloaded.strategyType()).isEqualTo(StrategyType.TOKEN_BUCKET);
        assertThat(reloaded.quota()).isEqualTo(newQuota);
    }

    @Test
    void disabledStatusIsPersisted() {
        TenantId tenantId = newSavedTenant("disable@acme.test");
        RateLimitResource resource = RateLimitResource.create(tenantId, "/login", StrategyType.FIXED_WINDOW,
                Quota.of(10, Duration.ofMinutes(1)));
        repository.save(resource).block();

        resource.disable();
        repository.save(resource).block();

        RateLimitResource reloaded = repository.findById(resource.id()).block();
        assertThat(reloaded.enabled()).isFalse();
    }
}
