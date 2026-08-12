package com.ratelimitservice.rls.adapter.redis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies, by inspecting the script sources themselves, that every strategy's Lua script derives
 * "now" from {@code redis.call('TIME')} and never accepts a caller-supplied timestamp: the
 * adapter only ever passes {@code ARGV[1]} (limit), {@code ARGV[2]} (window_ms) and
 * {@code ARGV[3]} (burst capacity) — see {@link RedisRateLimitEvaluationAdapter#evaluate}. This is
 * a fast, deterministic way to confirm the "Redis is the authoritative clock" requirement without
 * needing to fake clock skew against a real container.
 */
class RedisIsTheAuthoritativeClockTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "scripts/fixed_window.lua",
            "scripts/sliding_window_log.lua",
            "scripts/sliding_window_counter.lua",
            "scripts/token_bucket.lua",
            "scripts/leaky_bucket.lua"
    })
    void scriptSourcesTimeFromRedisAndNeverFromAFourthArgument(String classpathLocation) throws IOException {
        String source = readResource(classpathLocation);

        assertThat(source)
                .as("%s must derive 'now' from Redis's own clock", classpathLocation)
                .contains("redis.call('TIME')");
        assertThat(source)
                .as("%s must not accept a caller-supplied timestamp via a 4th argument", classpathLocation)
                .doesNotContain("ARGV[4]");
    }

    private static String readResource(String classpathLocation) throws IOException {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
