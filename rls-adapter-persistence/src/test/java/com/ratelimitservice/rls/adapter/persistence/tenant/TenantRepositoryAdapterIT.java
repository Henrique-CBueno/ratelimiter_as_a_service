package com.ratelimitservice.rls.adapter.persistence.tenant;

import com.ratelimitservice.rls.adapter.persistence.AbstractPostgresIT;
import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.tenant.ApiToken;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRepositoryAdapterIT extends AbstractPostgresIT {

    private final TenantRepositoryAdapter repository = new TenantRepositoryAdapter(DATABASE_CLIENT);

    private static Tenant newTenant(String email) {
        return Tenant.register("Acme Inc", email, "hashed-secret", FallbackPolicy.FAIL_OPEN);
    }

    @Test
    void savedTenantIsRetrievableByIdWithIdenticalState() {
        Tenant tenant = newTenant("ops@acme.test");

        repository.save(tenant).block();
        Tenant reloaded = repository.findById(tenant.id()).block();

        assertThat(reloaded.id()).isEqualTo(tenant.id());
        assertThat(reloaded.name()).isEqualTo(tenant.name());
        assertThat(reloaded.email()).isEqualTo(tenant.email());
        assertThat(reloaded.passwordHash()).isEqualTo(tenant.passwordHash());
        assertThat(reloaded.status()).isEqualTo(tenant.status());
        assertThat(reloaded.defaultFallbackPolicy()).isEqualTo(tenant.defaultFallbackPolicy());
    }

    @Test
    void findByEmailFindsAnExistingTenant() {
        Tenant tenant = newTenant("findme@acme.test");
        repository.save(tenant).block();

        Tenant found = repository.findByEmail("findme@acme.test").block();

        assertThat(found.id()).isEqualTo(tenant.id());
    }

    @Test
    void findByEmailReturnsEmptyForANonExistentTenant() {
        Tenant found = repository.findByEmail("nobody@acme.test").block();

        assertThat(found).isNull();
    }

    @Test
    void duplicateEmailIsRejected() {
        Tenant first = newTenant("duplicate@acme.test");
        repository.save(first).block();
        Tenant second = newTenant("duplicate@acme.test");

        assertThatThrownBy(() -> repository.save(second).block())
                .isInstanceOf(DuplicateEmailException.class);

        Tenant stillOriginal = repository.findByEmail("duplicate@acme.test").block();
        assertThat(stillOriginal.id()).isEqualTo(first.id());
    }

    @Test
    void issuedAndRotatedTokensArePersistedAndReloadable() {
        Tenant tenant = newTenant("tokens@acme.test");
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        tenant.issueApiToken("hash-1", "rls_live_ab12", issuedAt);
        repository.save(tenant).block();

        Instant rotatedAt = Instant.parse("2026-02-01T00:00:00Z");
        tenant.rotateApiToken("hash-2", "rls_live_cd34", rotatedAt);
        repository.save(tenant).block();

        Tenant reloaded = repository.findById(tenant.id()).block();
        assertThat(reloaded.apiTokens()).hasSize(2);
        ApiToken first = reloaded.apiTokens().stream().filter(t -> t.tokenHash().equals("hash-1")).findFirst().orElseThrow();
        ApiToken second = reloaded.apiTokens().stream().filter(t -> t.tokenHash().equals("hash-2")).findFirst().orElseThrow();
        assertThat(first.isActive()).isFalse();
        assertThat(first.revokedAt()).isEqualTo(rotatedAt);
        assertThat(second.isActive()).isTrue();
    }

    @Test
    void findByActiveTokenHashResolvesTheOwningTenant() {
        Tenant tenant = newTenant("active-token@acme.test");
        tenant.issueApiToken("active-hash", "rls_live_ab12", Instant.parse("2026-01-01T00:00:00Z"));
        repository.save(tenant).block();

        Tenant found = repository.findByActiveTokenHash("active-hash").block();

        assertThat(found.id()).isEqualTo(tenant.id());
    }

    @Test
    void findByActiveTokenHashReturnsEmptyForARevokedToken() {
        Tenant tenant = newTenant("revoked-token@acme.test");
        tenant.issueApiToken("revoked-hash", "rls_live_ab12", Instant.parse("2026-01-01T00:00:00Z"));
        tenant.rotateApiToken("new-hash", "rls_live_cd34", Instant.parse("2026-02-01T00:00:00Z"));
        repository.save(tenant).block();

        Tenant found = repository.findByActiveTokenHash("revoked-hash").block();

        assertThat(found).isNull();
    }
}
