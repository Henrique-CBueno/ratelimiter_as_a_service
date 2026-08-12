package com.ratelimitservice.rls.adapter.persistence.security;

import com.ratelimitservice.rls.application.security.port.SecretHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class BCryptSecretHasherAdapter implements SecretHasherPort {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawSecret) {
        return encoder.encode(rawSecret);
    }

    @Override
    public boolean matches(String rawSecret, String hash) {
        return encoder.matches(rawSecret, hash);
    }
}
