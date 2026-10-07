package com.regivolley.api;

import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the entire Spring context - domain, application, infrastructure,
 * wired against a real Postgres instance with Flyway applied - starts up without errors.
 */
@SpringBootTest
class RegiVolleyApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }
}
