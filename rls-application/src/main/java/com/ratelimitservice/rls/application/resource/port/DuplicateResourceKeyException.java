package com.ratelimitservice.rls.application.resource.port;

public final class DuplicateResourceKeyException extends RuntimeException {

    public DuplicateResourceKeyException(String resourceKey) {
        super("A resource with key '" + resourceKey + "' already exists for this tenant");
    }
}
