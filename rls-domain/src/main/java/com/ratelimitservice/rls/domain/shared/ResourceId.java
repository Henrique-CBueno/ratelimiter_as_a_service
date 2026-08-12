package com.ratelimitservice.rls.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record ResourceId(UUID value) {

    public ResourceId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ResourceId generate() {
        return new ResourceId(UUID.randomUUID());
    }

    public static ResourceId of(String value) {
        return new ResourceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
