package com.regivolley.api.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #40, threat model G3: outside profiles {@code dev} and {@code test} the application refuses to start without a complete,
 * encrypted SMTP configuration, because without it nobody could activate an account or reset a password.
 */
class StartupGuardMailTest {

    private static final String[] GOOD_MAIL = {
            "regi-volley.mail.host=smtp.example.org",
            "regi-volley.mail.port=587",
            "regi-volley.mail.username=mailer",
            "regi-volley.mail.password=a-long-random-smtp-secret",
            "regi-volley.mail.from=no-reply@example.org",
            "regi-volley.mail.security=STARTTLS",
            "regi-volley.security.web-origin=https://app.example.org"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(StartupGuardConfiguration.class))
            .withPropertyValues("spring.datasource.password=a-long-random-secret");

    @Test
    void prodStartsWithACompleteMailConfiguration() {
        // Arrange
        ApplicationContextRunner prod = runner.withPropertyValues("spring.profiles.active=prod").withPropertyValues(GOOD_MAIL);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
        });
    }

    @Test
    void prodRefusesToStartWithoutAMailHost() {
        // Arrange
        ApplicationContextRunner prod = runner.withPropertyValues("spring.profiles.active=prod");

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_HOST");
        });
    }

    @Test
    void theFailureNamesEveryProblemButNeverTheSecrets() {
        // Arrange
        ApplicationContextRunner prod = runner.withPropertyValues("spring.profiles.active=prod",
                "regi-volley.mail.host=smtp.example.org", "regi-volley.mail.security=none",
                "regi-volley.mail.password=a-long-random-smtp-secret");

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause()
                    .hasMessageContaining("SMTP_SECURITY").hasMessageContaining("SMTP_USERNAME")
                    .hasMessageContaining("MAIL_FROM").hasMessageContaining("WEB_ORIGIN")
                    .hasMessageNotContaining("a-long-random-smtp-secret");
        });
    }

    @Test
    void noProfileAtAllIsGuarded() {
        // Arrange
        ApplicationContextRunner none = runner;

        // Act
        none.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_HOST");
        });
    }

    @Test
    void anyOtherProfileIsGuarded() {
        // Arrange
        ApplicationContextRunner staging = runner.withPropertyValues("spring.profiles.active=staging");

        // Act
        staging.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_HOST");
        });
    }

    @Test
    void prodPlusDevStillNeedsMail() {
        // Arrange
        ApplicationContextRunner both = runner.withPropertyValues("spring.profiles.active=prod,dev");

        // Act
        both.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_HOST");
        });
    }

    @Test
    void devStartsWithoutAnyMailConfiguration() {
        // Arrange
        ApplicationContextRunner dev = runner.withPropertyValues("spring.profiles.active=dev", "spring.datasource.password=regi_volley");

        // Act
        dev.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
        });
    }

    @Test
    void testStartsWithoutAnyMailConfiguration() {
        // Arrange
        ApplicationContextRunner test = runner.withPropertyValues("spring.profiles.active=test", "spring.datasource.password=regi_volley");

        // Act
        test.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
        });
    }
}
