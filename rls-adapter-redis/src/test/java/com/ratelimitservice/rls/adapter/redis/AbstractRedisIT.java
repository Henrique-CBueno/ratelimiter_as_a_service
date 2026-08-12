package com.ratelimitservice.rls.adapter.redis;

import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

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
}
