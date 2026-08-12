package com.ratelimitservice.rls.adapter.persistence;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for PostgreSQL integration tests. Like {@code AbstractRedisIT} in
 * rls-adapter-redis, the container is started once (singleton pattern) and shared across every
 * test class in this module's run, with Flyway migrations applied a single time up front.
 */
public abstract class AbstractPostgresIT {

    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    protected static final ConnectionFactory CONNECTION_FACTORY;
    protected static final DatabaseClient DATABASE_CLIENT;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        POSTGRES_CONTAINER.start();

        runFlywayMigrations();

        CONNECTION_FACTORY = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES_CONTAINER.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES_CONTAINER.getMappedPort(5432))
                .option(ConnectionFactoryOptions.USER, POSTGRES_CONTAINER.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES_CONTAINER.getPassword())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES_CONTAINER.getDatabaseName())
                .build());
        DATABASE_CLIENT = DatabaseClient.create(CONNECTION_FACTORY);
    }

    private static void runFlywayMigrations() {
        Flyway.configure()
                .dataSource(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword())
                .load()
                .migrate();
    }

    @AfterEach
    void cleanDatabase() {
        DATABASE_CLIENT.sql("DELETE FROM api_tokens").then()
                .then(DATABASE_CLIENT.sql("DELETE FROM rate_limit_resources").then())
                .then(DATABASE_CLIENT.sql("DELETE FROM tenants").then())
                .block();
    }
}
