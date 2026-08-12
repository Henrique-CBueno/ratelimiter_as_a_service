package com.ratelimitservice.rls.application.security;

import com.ratelimitservice.rls.application.security.port.SecretHasherPort;

public final class FakeSecretHasherPort implements SecretHasherPort {

    @Override
    public String hash(String rawSecret) {
        return "hashed:" + rawSecret;
    }

    @Override
    public boolean matches(String rawSecret, String hash) {
        return hash.equals("hashed:" + rawSecret);
    }
}
