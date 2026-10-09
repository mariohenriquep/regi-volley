package com.regivolley.api.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * The start-up guards of a real deployment: this configuration is active under {@code prod} and under any profile that is neither
 * {@code dev} nor {@code test} (including none, whatever else is combined with {@code prod}), and stops the start when
 * <ul>
 *   <li>the database secret is blank or the local-development default {@code application.yml} falls back to (threat model D-3);</li>
 *   <li>the outbound email settings are incomplete, unencrypted or malformed (issue #40, threat model G3): without them nobody could
 *       activate an account or reset a password. The message names the missing environment variables, never their values.</li>
 * </ul>
 * The JWT key checks are in {@code JwtKeyConfiguration}; settings that are wrong under every profile (a bad SMTP port, no encryption to a
 * remote host) are refused by {@code MailConfiguration}.
 */
@Configuration
@Profile("prod | (!dev & !test)")
public class StartupGuardConfiguration {

    static final String DEVELOPMENT_DB_PASSWORD = "regi_volley";

    @Bean
    public SecretsCheck secretsCheck(Environment environment) {
        String password = environment.getProperty("spring.datasource.password");
        if (password == null || password.isBlank() || DEVELOPMENT_DB_PASSWORD.equals(password)) {
            throw new IllegalStateException("DB_PASSWORD must be set to a real secret unless profile dev or test is active");
        }
        return new SecretsCheck();
    }

    @Bean
    public MailCheck mailCheck(Environment environment) {
        List<String> problems = MailSettings.read(environment).productionProblems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Email is not configured for this profile (profile dev or test would allow it): "
                    + String.join("; ", problems));
        }
        return new MailCheck();
    }

    /** Marker bean: the email settings passed the check. */
    public static final class MailCheck {
    }

    /** Marker bean: its existence means the check passed. */
    public static final class SecretsCheck {
    }
}
