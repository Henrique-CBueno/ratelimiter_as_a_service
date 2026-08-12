package com.ratelimitservice.rls.adapter.rest.error;

import java.util.UUID;

/**
 * Raised by REST controllers (not the application/domain layer) when a resource doesn't exist for
 * the authenticated tenant — including when it exists but belongs to a different tenant, which is
 * treated identically per design decision 6 (avoids leaking that the id/key exists at all).
 */
public final class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forId(UUID resourceId) {
        return new ResourceNotFoundException("No resource found with id '" + resourceId + "' for the authenticated tenant");
    }

    public static ResourceNotFoundException forKey(String resourceKey) {
        return new ResourceNotFoundException("No resource found with key '" + resourceKey + "' for the authenticated tenant");
    }
}
