package com.ratelimitservice.rls.domain.tenant;

import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import com.ratelimitservice.rls.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantTest {

    // 4.1 Tenant registration

    @Test
    void registersTenantWithActiveStatus() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);

        assertThat(tenant.id()).isNotNull();
        assertThat(tenant.name()).isEqualTo("Acme Inc");
        assertThat(tenant.email()).isEqualTo("ops@acme.test");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.defaultFallbackPolicy()).isEqualTo(FallbackPolicy.FAIL_OPEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing-domain@", "@missing-local.test", "no-at-sign.test"})
    void rejectsInvalidEmail(String invalidEmail) {
        assertThatThrownBy(() -> Tenant.register("Acme Inc", invalidEmail, "hashed-secret", FallbackPolicy.FAIL_OPEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> Tenant.register(" ", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankPasswordHash() {
        assertThatThrownBy(() -> Tenant.register("Acme Inc", "ops@acme.test", " ", FallbackPolicy.FAIL_OPEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 4.3 Status lifecycle

    @Test
    void suspendsAnActiveTenant() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);

        tenant.suspend();

        assertThat(tenant.status()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void reactivatesASuspendedTenant() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
        tenant.suspend();

        tenant.reactivate();

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void cannotSuspendAnAlreadySuspendedTenant() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
        tenant.suspend();

        assertThatThrownBy(tenant::suspend).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotReactivateAnAlreadyActiveTenant() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);

        assertThatThrownBy(tenant::reactivate).isInstanceOf(IllegalStateException.class);
    }

    // 4.5 API token issuance and rotation

    @Test
    void issuesFirstApiTokenForATenant() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        ApiToken token = tenant.issueApiToken("token-hash-1", "rls_live_ab12", now);

        assertThat(token.tokenHash()).isEqualTo("token-hash-1");
        assertThat(token.tokenPrefix()).isEqualTo("rls_live_ab12");
        assertThat(token.createdAt()).isEqualTo(now);
        assertThat(token.revokedAt()).isNull();
        assertThat(token.isActive()).isTrue();
        assertThat(tenant.apiTokens()).containsExactly(token);
    }

    @Test
    void rotatingAnActiveTokenRevokesThePreviousOneAndIssuesANewOne() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        ApiToken original = tenant.issueApiToken("token-hash-1", "rls_live_ab12", issuedAt);

        Instant rotatedAt = Instant.parse("2026-02-01T00:00:00Z");
        ApiToken rotated = tenant.rotateApiToken("token-hash-2", "rls_live_cd34", rotatedAt);

        assertThat(original.isActive()).isFalse();
        assertThat(original.revokedAt()).isEqualTo(rotatedAt);
        assertThat(rotated.isActive()).isTrue();
        assertThat(tenant.apiTokens()).containsExactly(original, rotated);
    }

    @Test
    void rotatingWithNoActiveTokenJustIssuesANewOne() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        ApiToken token = tenant.rotateApiToken("token-hash-1", "rls_live_ab12", now);

        assertThat(token.isActive()).isTrue();
        assertThat(tenant.apiTokens()).containsExactly(token);
    }

    // 4.7 No plaintext credential exposure

    @Test
    void exposesOnlyThePasswordHashNeverAPlaintextPassword() {
        Tenant tenant = Tenant.register("Acme Inc", "ops@acme.test", "hashed-secret", FallbackPolicy.FAIL_OPEN);

        assertThat(tenant.passwordHash()).isEqualTo("hashed-secret");
        List<String> publicMethodNames = java.util.Arrays.stream(Tenant.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertThat(publicMethodNames)
                .as("Tenant must not expose a plaintext password accessor")
                .doesNotContain("plainPassword", "password", "rawPassword");
    }

    // 3.1 Reconstitution (spec: postgres-adapter) — reproduces an existing tenant's full state,
    // bypassing register()'s new-aggregate invariants (fresh id, ACTIVE status, empty tokens).

    @Test
    void reconstituteReproducesFullStateIncludingActiveAndRevokedTokens() {
        TenantId id = TenantId.generate();
        Instant issuedAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant revokedAt = Instant.parse("2025-06-01T00:00:00Z");
        Instant rotatedIssuedAt = Instant.parse("2025-06-01T00:00:00Z");
        List<ApiTokenData> tokenData = List.of(
                new ApiTokenData(UUID.randomUUID(), "hash-1", "rls_live_ab12", issuedAt, revokedAt),
                new ApiTokenData(UUID.randomUUID(), "hash-2", "rls_live_cd34", rotatedIssuedAt, null)
        );

        Tenant tenant = Tenant.reconstitute(id, "Acme Inc", "ops@acme.test", "hashed-secret",
                TenantStatus.SUSPENDED, FallbackPolicy.FAIL_CLOSED, tokenData);

        assertThat(tenant.id()).isEqualTo(id);
        assertThat(tenant.name()).isEqualTo("Acme Inc");
        assertThat(tenant.email()).isEqualTo("ops@acme.test");
        assertThat(tenant.passwordHash()).isEqualTo("hashed-secret");
        assertThat(tenant.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.defaultFallbackPolicy()).isEqualTo(FallbackPolicy.FAIL_CLOSED);

        assertThat(tenant.apiTokens()).hasSize(2);
        ApiToken firstToken = tenant.apiTokens().get(0);
        assertThat(firstToken.tokenHash()).isEqualTo("hash-1");
        assertThat(firstToken.tokenPrefix()).isEqualTo("rls_live_ab12");
        assertThat(firstToken.createdAt()).isEqualTo(issuedAt);
        assertThat(firstToken.revokedAt()).isEqualTo(revokedAt);
        assertThat(firstToken.isActive()).isFalse();

        ApiToken secondToken = tenant.apiTokens().get(1);
        assertThat(secondToken.tokenHash()).isEqualTo("hash-2");
        assertThat(secondToken.revokedAt()).isNull();
        assertThat(secondToken.isActive()).isTrue();
    }

    @Test
    void reconstituteWithNoTokensProducesAnEmptyTokenList() {
        Tenant tenant = Tenant.reconstitute(TenantId.generate(), "Acme Inc", "ops@acme.test",
                "hashed-secret", TenantStatus.ACTIVE, FallbackPolicy.FAIL_OPEN, List.of());

        assertThat(tenant.apiTokens()).isEmpty();
    }

    @Test
    void reconstituteDoesNotForceActiveStatusUnlikeRegister() {
        Tenant tenant = Tenant.reconstitute(TenantId.generate(), "Acme Inc", "ops@acme.test",
                "hashed-secret", TenantStatus.SUSPENDED, FallbackPolicy.FAIL_OPEN, List.of());

        assertThat(tenant.status()).isEqualTo(TenantStatus.SUSPENDED);
    }
}
