package com.ratelimitservice.rls.application.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates raw API tokens and computes their storage fingerprint. Unlike {@code SecretHasherPort}
 * (BCrypt, salted, for passwords), fingerprinting is a plain deterministic SHA-256 digest: a
 * random 24-byte token already has enough entropy to resist brute force without a salted, slow
 * hash, and the lookup by stored value ({@code TenantRepositoryPort.findByActiveTokenHash})
 * requires the same input to always produce the same output.
 */
public final class ApiTokenGenerator {

    private static final String PREFIX = "rls_live_";
    private static final int RANDOM_BYTE_LENGTH = 24;
    private static final int DISPLAY_PREFIX_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiTokenGenerator() {
    }

    public static String generate() {
        byte[] randomBytes = new byte[RANDOM_BYTE_LENGTH];
        RANDOM.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return PREFIX + randomPart;
    }

    public static String displayPrefix(String rawToken) {
        return rawToken.substring(0, Math.min(DISPLAY_PREFIX_LENGTH, rawToken.length()));
    }

    public static String fingerprint(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
