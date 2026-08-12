package com.ratelimitservice.rls.adapter.rest.auth;

/**
 * Exchange attribute key under which {@code ApiTokenAuthenticationWebFilter} (group 7) stores the
 * resolved {@code Tenant} for downstream handlers to read.
 */
public final class AuthenticatedTenant {

    public static final String ATTRIBUTE = AuthenticatedTenant.class.getName();

    private AuthenticatedTenant() {
    }
}
