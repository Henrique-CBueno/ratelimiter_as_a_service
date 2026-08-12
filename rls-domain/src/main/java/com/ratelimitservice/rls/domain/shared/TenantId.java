package com.ratelimitservice.rls.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
