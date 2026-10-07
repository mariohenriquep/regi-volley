package com.regivolley.api.infrastructure;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real PostgreSQL instance.
 *
 * <p>Singleton container pattern: the container is started once, in the static initialiser, and is
 * left running until the JVM exits (Testcontainers' Ryuk reaper removes it). It is deliberately
 * not managed by the JUnit {@code @Testcontainers}/{@code @Container} extension, which stops a
 * static container after every test class: Spring caches application contexts across classes, so
 * the next class would reuse a context whose datasource still points at the stopped container's
 * port and every test would time out acquiring a connection.
 */
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    // Same as application.yml: no row contents (personal data) in constraint-violation messages.
                    .withUrlParam("logServerErrorDetail", "false");

    static {
        POSTGRES.start();
    }
}
