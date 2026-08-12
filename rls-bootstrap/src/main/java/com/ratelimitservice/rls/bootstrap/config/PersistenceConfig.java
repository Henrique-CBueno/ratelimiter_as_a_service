package com.ratelimitservice.rls.bootstrap.config;

import com.ratelimitservice.rls.adapter.persistence.resource.ResourceRepositoryAdapter;
import com.ratelimitservice.rls.adapter.persistence.security.BCryptSecretHasherAdapter;
import com.ratelimitservice.rls.adapter.persistence.tenant.TenantRepositoryAdapter;
import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.application.security.port.SecretHasherPort;
import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Wires the spec 3 PostgreSQL/R2DBC adapter by hand (design decision 5). Flyway migrations
 * ({@code flyway} bean) are forced to run before the R2DBC {@code databaseClient} bean is created
 * via {@code @DependsOn}, since Spring's bean graph gives no ordering guarantee between beans that
 * don't otherwise depend on each other.
 */
@Configuration
public class PersistenceConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(@Value("${rls.postgres.host}") String host,
                          @Value("${rls.postgres.port}") int port,
                          @Value("${rls.postgres.database}") String database,
                          @Value("${rls.postgres.username}") String username,
                          @Value("${rls.postgres.password}") String password) {
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        return Flyway.configure().dataSource(jdbcUrl, username, password).load();
    }

    @Bean
    @DependsOn("flyway")
    public ConnectionFactory connectionFactory(@Value("${rls.postgres.host}") String host,
                                                @Value("${rls.postgres.port}") int port,
                                                @Value("${rls.postgres.database}") String database,
                                                @Value("${rls.postgres.username}") String username,
                                                @Value("${rls.postgres.password}") String password) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    @Bean
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    public TenantRepositoryPort tenantRepositoryPort(DatabaseClient databaseClient) {
        return new TenantRepositoryAdapter(databaseClient);
    }

    @Bean
    public ResourceRepositoryPort resourceRepositoryPort(DatabaseClient databaseClient) {
        return new ResourceRepositoryAdapter(databaseClient);
    }

    @Bean
    public SecretHasherPort secretHasherPort() {
        return new BCryptSecretHasherAdapter();
    }
}
