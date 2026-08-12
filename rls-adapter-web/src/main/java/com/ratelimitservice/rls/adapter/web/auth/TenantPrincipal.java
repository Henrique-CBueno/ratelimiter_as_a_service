package com.ratelimitservice.rls.adapter.web.auth;

import com.ratelimitservice.rls.domain.shared.TenantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal for the {@code /app/**} session, carrying the tenant's id so
 * dashboard controllers can scope every use case call without a repository lookup per request.
 * {@code UserDetails} extends {@code Serializable} (Spring Session persists the whole
 * {@code SecurityContext}, including this principal, to Redis), so this holds a raw {@link UUID}
 * rather than the domain {@link TenantId} record — keeping domain types free of a
 * serialization-infrastructure concern that belongs to this adapter, not {@code rls-domain}.
 */
public final class TenantPrincipal implements UserDetails {

    private final UUID tenantId;
    private final String email;

    public TenantPrincipal(TenantId tenantId, String email) {
        this.tenantId = tenantId.value();
        this.email = email;
    }

    public TenantId tenantId() {
        return new TenantId(tenantId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
