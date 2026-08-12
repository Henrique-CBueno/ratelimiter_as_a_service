package com.ratelimitservice.rls.adapter.persistence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends AbstractPostgresIT {

    @ParameterizedTest
    @ValueSource(strings = {"tenants", "api_tokens", "rate_limit_resources"})
    void migrationCreatesExpectedTable(String tableName) {
        Long count = DATABASE_CLIENT.sql(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = :tableName")
                .bind("tableName", tableName)
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        assertThat(count).as("table %s should exist", tableName).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "uq_tenants_email",
            "uq_api_tokens_token_hash",
            "uq_rate_limit_resources_tenant_key"
    })
    void migrationCreatesExpectedUniqueConstraint(String constraintName) {
        Long count = DATABASE_CLIENT.sql(
                        "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name = :constraintName")
                .bind("constraintName", constraintName)
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        assertThat(count).as("constraint %s should exist", constraintName).isEqualTo(1L);
    }

    @org.junit.jupiter.api.Test
    void allExpectedTablesArePresent() {
        List<String> tables = DATABASE_CLIENT.sql(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")
                .map(row -> row.get(0, String.class))
                .all()
                .collectList()
                .block();

        // also includes Flyway's own "flyway_schema_history" bookkeeping table, which is not
        // one of this migration's application tables and is intentionally not asserted against
        assertThat(tables).contains("api_tokens", "rate_limit_resources", "tenants");
    }
}
