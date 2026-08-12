package com.ratelimitservice.rls.application.security.port;

public interface SecretHasherPort {

    String hash(String rawSecret);

    boolean matches(String rawSecret, String hash);
}
