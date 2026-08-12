package com.ratelimitservice.rls.bootstrap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers (Redis + PostgreSQL) setup for every {@code rls-bootstrap} integration
 * test. Deliberately NOT using {@code @Testcontainers}/{@code @Container}: that extension ties
 * container start/stop to each concrete test class's own lifecycle, so with an abstract base class
 * shared by multiple test classes it stops the containers after the first class's tests and
 * restarts them (on new random ports) for the next - while Spring reuses the cached
 * ApplicationContext (identical config across classes) with its R2DBC pool still bound to the dead
 * port. Starting once in a static initializer and never stopping (Ryuk reaps it at JVM exit) is the
 * documented Testcontainers "singleton container" pattern that avoids this.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractE2ETest {

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        REDIS.start();
        POSTGRES.start();
    }

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

    @Autowired
    protected WebTestClient webTestClient;
}
