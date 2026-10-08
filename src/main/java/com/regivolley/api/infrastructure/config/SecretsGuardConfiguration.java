package com.regivolley.api.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Start-up check on the database secret (threat model D-3): the local-development password that {@code application.yml} falls
 * back to must boot only where a developer is explicitly running, i.e. under profile {@code dev} or {@code test}. Under any
 * other profile (including none, and {@code prod}, whatever else is combined with it) a blank or default
 * {@code DB_PASSWORD} refuses to start. The JWT key checks are in {@code JwtKeyConfiguration}.
 */
@Configuration
@Profile("!dev & !test")
public class SecretsGuardConfiguration {

    static final String DEVELOPMENT_DB_PASSWORD = "regi_volley";

    @Bean
    public SecretsCheck secretsCheck(Environment environment) {
        String password = environment.getProperty("spring.datasource.password");
        if (password == null || password.isBlank() || DEVELOPMENT_DB_PASSWORD.equals(password)) {
            throw new IllegalStateException("DB_PASSWORD must be set to a real secret unless profile dev or test is active");
        }
        return new SecretsCheck();
    }

    /** Marker bean: its existence means the check passed. */
    public static final class SecretsCheck {
    }
}
