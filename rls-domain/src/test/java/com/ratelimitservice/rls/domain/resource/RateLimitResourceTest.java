package com.ratelimitservice.rls.domain.resource;

import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitResourceTest {

    private static final TenantId TENANT_ID = TenantId.generate();
    private static final Quota QUOTA = Quota.of(100, Duration.ofMinutes(1));

    // 5.1 Resource creation

    @Test
    void createsAResourceWithGivenConfiguration() {
        RateLimitResource resource = RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, QUOTA);

        assertThat(resource.id()).isNotNull();
        assertThat(resource.tenantId()).isEqualTo(TENANT_ID);
        assertThat(resource.resourceKey()).isEqualTo("/login");
        assertThat(resource.strategyType()).isEqualTo(StrategyType.FIXED_WINDOW);
        assertThat(resource.quota()).isEqualTo(QUOTA);
        assertThat(resource.enabled()).isTrue();
    }

    @Test
    void rejectsCreationWithNullQuota() {
        // Quota itself makes non-positive limit/window unrepresentable (see QuotaTest), so the
        // "non-positive quota" case from the spec is exercised here via a null quota instead.
        assertThatThrownBy(() -> RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsCreationWithBlankResourceKey() {
        assertThatThrownBy(() -> RateLimitResource.create(TENANT_ID, " ", StrategyType.FIXED_WINDOW, QUOTA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 5.1 (uniqueness support) Domain exposes tenantId + resourceKey

    @Test
    void exposesTenantIdAndResourceKeyForUniquenessChecks() {
        RateLimitResource resource = RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, QUOTA);

        assertThat(resource.tenantId()).isEqualTo(TENANT_ID);
        assertThat(resource.resourceKey()).isEqualTo("/login");
    }

    // 5.3 Fallback policy inheritance

    @Test
    void resourceWithoutExplicitFallbackPolicyInheritsFromTenant() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_CLOSED);
        RateLimitResource resource = RateLimitResource.create(tenant.id(), "/login", StrategyType.FIXED_WINDOW, QUOTA);

        assertThat(resource.fallbackPolicy()).isNull();
        assertThat(resource.resolveFallbackPolicy(tenant)).isEqualTo(FallbackPolicy.FAIL_CLOSED);
    }

    @Test
    void resourceWithExplicitFallbackPolicyOverridesTenantDefault() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_CLOSED);
        RateLimitResource resource = RateLimitResource.create(
                tenant.id(), "/login", StrategyType.FIXED_WINDOW, QUOTA, FallbackPolicy.FAIL_OPEN);

        assertThat(resource.fallbackPolicy()).isEqualTo(FallbackPolicy.FAIL_OPEN);
        assertThat(resource.resolveFallbackPolicy(tenant)).isEqualTo(FallbackPolicy.FAIL_OPEN);
    }

    // 5.5 Enable / disable

    @Test
    void disablingAnEnabledResource() {
        RateLimitResource resource = RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, QUOTA);

        resource.disable();

        assertThat(resource.enabled()).isFalse();
    }

    @Test
    void reEnablingADisabledResource() {
        RateLimitResource resource = RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, QUOTA);
        resource.disable();

        resource.enable();

        assertThat(resource.enabled()).isTrue();
    }

    // 5.7 Reconfiguration

    @Test
    void reconfiguresStrategyAndQuotaKeepingIdentityStable() {
        RateLimitResource resource = RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, QUOTA);
        var resourceId = resource.id();
        Quota newQuota = Quota.of(50, Duration.ofSeconds(30));

        resource.reconfigure(StrategyType.TOKEN_BUCKET, newQuota);

        assertThat(resource.id()).isEqualTo(resourceId);
        assertThat(resource.tenantId()).isEqualTo(TENANT_ID);
        assertThat(resource.resourceKey()).isEqualTo("/login");
        assertThat(resource.strategyType()).isEqualTo(StrategyType.TOKEN_BUCKET);
        assertThat(resource.quota()).isEqualTo(newQuota);
    }

    @Test
    void reconfigurationRejectedWithNullQuotaKeepsPreviousConfiguration() {
        RateLimitResource resource = RateLimitResource.create(TENANT_ID, "/login", StrategyType.FIXED_WINDOW, QUOTA);

        assertThatThrownBy(() -> resource.reconfigure(StrategyType.TOKEN_BUCKET, null))
                .isInstanceOf(RuntimeException.class);

        assertThat(resource.strategyType()).isEqualTo(StrategyType.FIXED_WINDOW);
        assertThat(resource.quota()).isEqualTo(QUOTA);
    }
}
