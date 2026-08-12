package com.ratelimitservice.rls.application.tenant.port;

public final class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("A tenant with email '" + email + "' already exists");
    }
}
