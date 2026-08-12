package com.ratelimitservice.rls.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RlsApplicationSmokeIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("rls.redis.host", REDIS::getHost);
        registry.add("rls.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("rls.postgres.host", POSTGRES::getHost);
        registry.add("rls.postgres.port", () -> POSTGRES.getMappedPort(5432));
        registry.add("rls.postgres.database", POSTGRES::getDatabaseName);
        registry.add("rls.postgres.username", POSTGRES::getUsername);
        registry.add("rls.postgres.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoadsWithEveryBeanWired() {
        // If the context fails to start (missing bean, Flyway migration failure, bad Redis/R2DBC
        // connection wiring), this test fails during Spring's context startup, before this body
        // ever runs.
    }
}
