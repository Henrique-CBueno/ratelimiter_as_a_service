package com.ratelimitservice.rls.domain.tenant;

import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

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
}
