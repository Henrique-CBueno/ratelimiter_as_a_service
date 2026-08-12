package com.ratelimitservice.rls.adapter.persistence.tenant;

import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import com.ratelimitservice.rls.domain.tenant.ApiToken;
import com.ratelimitservice.rls.domain.tenant.ApiTokenData;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import com.ratelimitservice.rls.domain.tenant.TenantStatus;
import io.r2dbc.spi.R2dbcException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public final class TenantRepositoryAdapter implements TenantRepositoryPort {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final DatabaseClient databaseClient;

    public TenantRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Tenant> save(Tenant tenant) {
        Mono<Void> upsertTenant = databaseClient.sql("""
                        INSERT INTO tenants (id, name, email, password_hash, default_fallback_policy, status, created_at, updated_at)
                        VALUES (:id, :name, :email, :passwordHash, :defaultFallbackPolicy, :status, now(), now())
                        ON CONFLICT (id) DO UPDATE SET
                            name = EXCLUDED.name,
                            email = EXCLUDED.email,
                            password_hash = EXCLUDED.password_hash,
                            default_fallback_policy = EXCLUDED.default_fallback_policy,
                            status = EXCLUDED.status,
                            updated_at = now()
                        """)
                .bind("id", tenant.id().value())
                .bind("name", tenant.name())
                .bind("email", tenant.email())
                .bind("passwordHash", tenant.passwordHash())
                .bind("defaultFallbackPolicy", tenant.defaultFallbackPolicy().name())
                .bind("status", tenant.status().name())
                .then()
                .onErrorMap(TenantRepositoryAdapter::isUniqueViolation,
                        ex -> new DuplicateEmailException(tenant.email()));

        Mono<Void> upsertTokens = Flux.fromIterable(tenant.apiTokens())
                .concatMap(token -> upsertToken(tenant.id().value(), token))
                .then();

        return upsertTenant.then(upsertTokens).thenReturn(tenant);
    }

    private Mono<Void> upsertToken(UUID tenantId, ApiToken token) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO api_tokens (id, tenant_id, token_hash, token_prefix, created_at, revoked_at)
                        VALUES (:id, :tenantId, :tokenHash, :tokenPrefix, :createdAt, :revokedAt)
                        ON CONFLICT (id) DO UPDATE SET revoked_at = EXCLUDED.revoked_at
                        """)
                .bind("id", token.id())
                .bind("tenantId", tenantId)
                .bind("tokenHash", token.tokenHash())
                .bind("tokenPrefix", token.tokenPrefix())
                .bind("createdAt", token.createdAt());
        spec = bindNullableInstant(spec, "revokedAt", token.revokedAt());
        return spec.then();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullableInstant(
            DatabaseClient.GenericExecuteSpec spec, String name, Instant value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, Instant.class);
    }

    @Override
    public Mono<Tenant> findById(TenantId id) {
        return databaseClient.sql("SELECT * FROM tenants WHERE id = :id")
                .bind("id", id.value())
                .map(TenantRow::from)
                .first()
                .flatMap(this::toTenant);
    }

    @Override
    public Mono<Tenant> findByEmail(String email) {
        return databaseClient.sql("SELECT * FROM tenants WHERE email = :email")
                .bind("email", email)
                .map(TenantRow::from)
                .first()
                .flatMap(this::toTenant);
    }

    @Override
    public Mono<Tenant> findByActiveTokenHash(String tokenHash) {
        return databaseClient.sql("SELECT tenant_id FROM api_tokens WHERE token_hash = :tokenHash AND revoked_at IS NULL")
                .bind("tokenHash", tokenHash)
                .map(row -> row.get("tenant_id", UUID.class))
                .first()
                .flatMap(tenantId -> findById(new TenantId(tenantId)));
    }

    private Mono<Tenant> toTenant(TenantRow row) {
        return loadTokens(row.id())
                .collectList()
                .map(tokens -> Tenant.reconstitute(new TenantId(row.id()), row.name(), row.email(),
                        row.passwordHash(), TenantStatus.valueOf(row.status()),
                        FallbackPolicy.valueOf(row.defaultFallbackPolicy()), tokens));
    }

    private Flux<ApiTokenData> loadTokens(UUID tenantId) {
        return databaseClient.sql(
                        "SELECT id, token_hash, token_prefix, created_at, revoked_at FROM api_tokens WHERE tenant_id = :tenantId ORDER BY created_at")
                .bind("tenantId", tenantId)
                .map(row -> new ApiTokenData(
                        row.get("id", UUID.class),
                        row.get("token_hash", String.class),
                        row.get("token_prefix", String.class),
                        row.get("created_at", Instant.class),
                        row.get("revoked_at", Instant.class)))
                .all();
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

    private record TenantRow(UUID id, String name, String email, String passwordHash,
                              String defaultFallbackPolicy, String status) {

        static TenantRow from(io.r2dbc.spi.Readable row) {
            return new TenantRow(
                    row.get("id", UUID.class),
                    row.get("name", String.class),
                    row.get("email", String.class),
                    row.get("password_hash", String.class),
                    row.get("default_fallback_policy", String.class),
                    row.get("status", String.class));
        }
    }
}
