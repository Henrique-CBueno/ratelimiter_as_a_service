package com.ratelimitservice.rls.adapter.persistence.resource;

import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.ResourceId;
import com.ratelimitservice.rls.domain.shared.TenantId;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

public final class ResourceRepositoryAdapter implements ResourceRepositoryPort {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final DatabaseClient databaseClient;

    public ResourceRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<RateLimitResource> save(RateLimitResource resource) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO rate_limit_resources
                            (id, tenant_id, resource_key, strategy_type, limit_count, window_seconds,
                             burst_capacity, fallback_policy, enabled, created_at, updated_at)
                        VALUES
                            (:id, :tenantId, :resourceKey, :strategyType, :limitCount, :windowSeconds,
                             :burstCapacity, :fallbackPolicy, :enabled, now(), now())
                        ON CONFLICT (id) DO UPDATE SET
                            strategy_type = EXCLUDED.strategy_type,
                            limit_count = EXCLUDED.limit_count,
                            window_seconds = EXCLUDED.window_seconds,
                            burst_capacity = EXCLUDED.burst_capacity,
                            fallback_policy = EXCLUDED.fallback_policy,
                            enabled = EXCLUDED.enabled,
                            updated_at = now()
                        """)
                .bind("id", resource.id().value())
                .bind("tenantId", resource.tenantId().value())
                .bind("resourceKey", resource.resourceKey())
                .bind("strategyType", resource.strategyType().name())
                .bind("limitCount", resource.quota().limit())
                .bind("windowSeconds", (int) resource.quota().window().toSeconds())
                .bind("enabled", resource.enabled());
        spec = bindNullableInt(spec, "burstCapacity", resource.quota().burstCapacity());
        spec = bindNullableString(spec, "fallbackPolicy",
                resource.fallbackPolicy() != null ? resource.fallbackPolicy().name() : null);

        return spec.then()
                .onErrorMap(ResourceRepositoryAdapter::isUniqueViolation,
                        ex -> new DuplicateResourceKeyException(resource.resourceKey()))
                .thenReturn(resource);
    }

    @Override
    public Mono<RateLimitResource> findById(ResourceId id) {
        return databaseClient.sql("SELECT * FROM rate_limit_resources WHERE id = :id")
                .bind("id", id.value())
                .map(ResourceRepositoryAdapter::toResource)
                .first();
    }

    @Override
    public Mono<RateLimitResource> findByTenantAndKey(TenantId tenantId, String resourceKey) {
        return databaseClient.sql("SELECT * FROM rate_limit_resources WHERE tenant_id = :tenantId AND resource_key = :resourceKey")
                .bind("tenantId", tenantId.value())
                .bind("resourceKey", resourceKey)
                .map(ResourceRepositoryAdapter::toResource)
                .first();
    }

    @Override
    public Flux<RateLimitResource> findAllByTenant(TenantId tenantId) {
        return databaseClient.sql("SELECT * FROM rate_limit_resources WHERE tenant_id = :tenantId ORDER BY created_at")
                .bind("tenantId", tenantId.value())
                .map(ResourceRepositoryAdapter::toResource)
                .all();
    }

    private static RateLimitResource toResource(Readable row) {
        UUID id = row.get("id", UUID.class);
        UUID tenantId = row.get("tenant_id", UUID.class);
        String resourceKey = row.get("resource_key", String.class);
        StrategyType strategyType = StrategyType.valueOf(row.get("strategy_type", String.class));
        int limitCount = row.get("limit_count", Integer.class);
        int windowSeconds = row.get("window_seconds", Integer.class);
        Integer burstCapacity = row.get("burst_capacity", Integer.class);
        String fallbackPolicyName = row.get("fallback_policy", String.class);
        boolean enabled = row.get("enabled", Boolean.class);

        Quota quota = burstCapacity != null
                ? Quota.withBurst(limitCount, Duration.ofSeconds(windowSeconds), burstCapacity)
                : Quota.of(limitCount, Duration.ofSeconds(windowSeconds));
        FallbackPolicy fallbackPolicy = fallbackPolicyName != null ? FallbackPolicy.valueOf(fallbackPolicyName) : null;

        return RateLimitResource.reconstitute(new ResourceId(id), new TenantId(tenantId), resourceKey,
                strategyType, quota, fallbackPolicy, enabled);
    }

    private static DatabaseClient.GenericExecuteSpec bindNullableInt(
            DatabaseClient.GenericExecuteSpec spec, String name, Integer value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, Integer.class);
    }

    private static DatabaseClient.GenericExecuteSpec bindNullableString(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, String.class);
    }

    private static boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof R2dbcException r2dbcException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(r2dbcException.getSqlState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
