package com.ratelimitservice.rls.adapter.rest.tenant;

import java.util.UUID;

public record RegisterTenantResponse(UUID tenantId, String apiToken) {
}
