package com.ratelimitservice.rls.adapter.rest.tenant;

public record RegisterTenantRequest(String name, String email, String password) {
}
