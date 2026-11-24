package com.shelf.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Provides isolated JDBC credentials before Spring evaluates datasource and Flyway conditions. */
public abstract class TestcontainersConfig {
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
        var db = TestDatabase.INSTANCE;
        properties.add("spring.datasource.url", db::url);
        properties.add("spring.datasource.username", db::user);
        properties.add("spring.datasource.password", db::password);
        properties.add("spring.flyway.url", db::url);
        properties.add("spring.flyway.user", db::user);
        properties.add("spring.flyway.password", db::password);
    }

    private record TestDatabase(String url, String user, String password) {
        private static final TestDatabase INSTANCE = start();

        private static TestDatabase start() {
            String external = System.getenv("SHELF_TEST_DB_URL");
            if (external != null && !external.isBlank()) {
                return new TestDatabase(external, System.getenv("SHELF_TEST_DB_USER"), System.getenv("SHELF_TEST_DB_PASSWORD"));
            }
            var postgres = new PostgreSQLContainer("postgres:17-alpine");
            postgres.start();
            Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop, "stop-test-postgres"));
            return new TestDatabase(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }
    }
}
