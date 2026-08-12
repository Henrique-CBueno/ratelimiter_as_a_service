package com.ratelimitservice.rls.adapter.redis;

import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * Base class for Redis integration tests. The container is started once (singleton pattern) and
 * shared across every subclass/test-class in this module's run, rather than per test class, so
 * the many strategy/parity/concurrency test classes in this module don't each pay container
 * startup cost. Testcontainers' Ryuk reaper cleans it up when the JVM exits.
 */
public abstract class AbstractRedisIT {

    protected static final GenericContainer<?> REDIS_CONTAINER;
    protected static final ReactiveRedisTemplate<String, String> REDIS_TEMPLATE;

    static {
        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS_CONTAINER.start();

        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        REDIS_TEMPLATE = new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
    }

    @AfterEach
    void flushRedis() {
        REDIS_TEMPLATE.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
    }

    /**
     * Reads Redis's own current time, in milliseconds. Tests that need to pre-seed state at a
     * specific point relative to "now" must anchor to this instead of the JVM's clock, since the
     * scripts themselves only ever consult Redis's clock (never the caller's) — using a different
     * clock to seed would make such tests flaky.
     */
    protected static long fetchRedisNowMs() {
        RedisScript<String> script = RedisScript.of(
                "local t = redis.call('TIME') return tostring(math.floor(tonumber(t[1]) * 1000 + tonumber(t[2]) / 1000))",
                String.class);
        return Long.parseLong(REDIS_TEMPLATE.execute(script, List.of()).blockFirst());
    }
}
