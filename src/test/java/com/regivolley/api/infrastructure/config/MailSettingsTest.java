package com.regivolley.api.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/** Issue #40: the SMTP settings as read from the environment, and what a production start-up requires of them. */
class MailSettingsTest {

    private static MockEnvironment complete() {
        return new MockEnvironment()
                .withProperty("regi-volley.mail.host", "smtp.example.org")
                .withProperty("regi-volley.mail.port", "587")
                .withProperty("regi-volley.mail.username", "mailer")
                .withProperty("regi-volley.mail.password", "s3cret-smtp-password")
                .withProperty("regi-volley.mail.from", "RegiVolley <no-reply@example.org>")
                .withProperty("regi-volley.mail.security", "starttls")
                .withProperty("regi-volley.security.web-origin", "https://app.example.org");
    }

    @Test
    void readsEverySettingFromTheEnvironment() {
        // Arrange
        MockEnvironment environment = complete();

        // Act
        MailSettings settings = MailSettings.read(environment);

        // Assert
        assertThat(settings.configured()).isTrue();
        assertThat(settings.host()).isEqualTo("smtp.example.org");
        assertThat(settings.port()).isEqualTo(587);
        assertThat(settings.username()).isEqualTo("mailer");
        assertThat(settings.from()).isEqualTo("RegiVolley <no-reply@example.org>");
        assertThat(settings.security()).isEqualTo(MailSettings.Security.STARTTLS);
        assertThat(settings.webOrigin()).isEqualTo("https://app.example.org");
    }

    @Test
    void aBlankHostMeansMailIsNotConfigured() {
        // Arrange
        MockEnvironment environment = new MockEnvironment().withProperty("regi-volley.mail.host", "  ");

        // Act
        MailSettings settings = MailSettings.read(environment);

        // Assert
        assertThat(settings.configured()).isFalse();
    }

    @Test
    void theDefaultsAreSubmissionPortWithStartTls() {
        // Arrange
        MockEnvironment environment = new MockEnvironment().withProperty("regi-volley.mail.host", "smtp.example.org");

        // Act
        MailSettings settings = MailSettings.read(environment);

        // Assert
        assertThat(settings.port()).isEqualTo(587);
        assertThat(settings.security()).isEqualTo(MailSettings.Security.STARTTLS);
    }

    @Test
    void aCompleteProductionConfigurationHasNoProblems() {
        // Arrange
        MailSettings settings = MailSettings.read(complete());

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(problems).isEmpty();
    }

    @Test
    void everyMissingProductionSettingIsNamedByItsEnvironmentVariable() {
        // Arrange
        MailSettings settings = MailSettings.read(new MockEnvironment());

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(String.join(" ", problems)).contains("SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD", "MAIL_FROM", "WEB_ORIGIN");
    }

    @Test
    void productionRefusesAMailServerWithoutEncryption() {
        // Arrange
        MailSettings settings = MailSettings.read(complete().withProperty("regi-volley.mail.security", "none"));

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(String.join(" ", problems)).contains("SMTP_SECURITY");
    }

    @Test
    void anUnknownSecurityModeIsAProblemNotASilentDowngrade() {
        // Arrange
        MailSettings settings = MailSettings.read(complete().withProperty("regi-volley.mail.security", "ssl3"));

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(String.join(" ", problems)).contains("SMTP_SECURITY");
    }

    @Test
    void productionRefusesAFromAddressThatIsNotAnAddress() {
        // Arrange
        MailSettings settings = MailSettings.read(complete().withProperty("regi-volley.mail.from", "not an address"));

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(String.join(" ", problems)).contains("MAIL_FROM");
    }

    @Test
    void productionRefusesAPortOutsideTheRange() {
        // Arrange
        MailSettings settings = MailSettings.read(complete().withProperty("regi-volley.mail.port", "70000"));

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(String.join(" ", problems)).contains("SMTP_PORT");
    }

    @Test
    void productionNeedsAnHttpsWebOriginWithoutAPath() {
        // Arrange
        MailSettings plain = MailSettings.read(complete().withProperty("regi-volley.security.web-origin", "http://app.example.org"));
        MailSettings withPath = MailSettings.read(complete().withProperty("regi-volley.security.web-origin", "https://app.example.org/app"));

        // Act
        var plainProblems = plain.productionProblems();
        var pathProblems = withPath.productionProblems();

        // Assert
        assertThat(String.join(" ", plainProblems)).contains("WEB_ORIGIN");
        assertThat(String.join(" ", pathProblems)).contains("WEB_ORIGIN");
    }

    @Test
    void theWebOriginFallsBackToTheLocalFrontendOnlyWhenNothingIsSet() {
        // Arrange
        MailSettings unset = MailSettings.read(new MockEnvironment().withProperty("regi-volley.mail.host", "localhost"));
        MailSettings set = MailSettings.read(complete());

        // Act
        String fallback = unset.linkOrigin();
        String configured = set.linkOrigin();

        // Assert
        assertThat(fallback).isEqualTo("http://localhost:5173");
        assertThat(configured).isEqualTo("https://app.example.org");
    }

    @Test
    void toStringNeverShowsThePasswordOrTheUsername() {
        // Arrange
        MailSettings settings = MailSettings.read(complete());

        // Act
        String text = settings.toString();

        // Assert
        assertThat(text).doesNotContain("s3cret-smtp-password").doesNotContain("mailer");
    }

    @Test
    void noEncryptionIsAllowedOnlyForALoopbackHostOrMailpit() {
        // Arrange
        for (String host : new String[] {"localhost", "LOCALHOST", "127.0.0.1", "127.1.2.3", "::1", "[::1]", "mailpit"}) {
            MailSettings local = MailSettings.read(complete().withProperty("regi-volley.mail.host", host)
                    .withProperty("regi-volley.mail.security", "none"));

            // Act
            var problems = local.structuralProblems();

            // Assert
            assertThat(problems).as(host).isEmpty();
        }
    }

    @Test
    void noEncryptionToAnyOtherHostIsRefusedUnderEveryProfile() {
        // Arrange
        for (String host : new String[] {"smtp.example.org", "10.0.0.5", "mailpit.evil.example", "localhost.example.org", "127.0.0.1.example.org"}) {
            MailSettings remote = MailSettings.read(complete().withProperty("regi-volley.mail.host", host)
                    .withProperty("regi-volley.mail.security", "none"));

            // Act
            var problems = remote.structuralProblems();

            // Assert
            assertThat(String.join(" ", problems)).as(host).contains("SMTP_SECURITY");
        }
    }

    @Test
    void structuralProblemsCoverPortSecurityAndAMalformedFromAndNothingElse() {
        // Arrange
        MailSettings nothingSet = MailSettings.read(new MockEnvironment().withProperty("regi-volley.mail.host", "localhost"));
        MailSettings broken = MailSettings.read(complete().withProperty("regi-volley.mail.port", "0")
                .withProperty("regi-volley.mail.security", "ssl3").withProperty("regi-volley.mail.from", "not an address"));

        // Act
        var none = nothingSet.structuralProblems();
        var all = String.join(" ", broken.structuralProblems());

        // Assert
        assertThat(none).isEmpty();
        assertThat(all).contains("SMTP_PORT", "SMTP_SECURITY", "MAIL_FROM");
    }

    @Test
    void productionProblemsIncludeTheStructuralOnesOnce() {
        // Arrange
        MailSettings settings = MailSettings.read(complete().withProperty("regi-volley.mail.port", "abc"));

        // Act
        var problems = settings.productionProblems();

        // Assert
        assertThat(problems).filteredOn(problem -> problem.contains("SMTP_PORT")).hasSize(1);
    }

    @Test
    void aTrailingSlashOnTheWebOriginIsNormalisedAwayInOnePlace() {
        // Arrange
        MailSettings settings = MailSettings.read(complete().withProperty("regi-volley.security.web-origin", "https://app.example.org/"));

        // Act
        String origin = settings.linkOrigin();

        // Assert
        assertThat(origin).isEqualTo("https://app.example.org");
        assertThat(settings.productionProblems()).isEmpty();
    }

    @Test
    void aPortThatDoesNotMatchTheSecurityModeIsWarnedAbout() {
        // Arrange
        MailSettings implicitOnSubmission = MailSettings.read(complete().withProperty("regi-volley.mail.security", "tls")
                .withProperty("regi-volley.mail.port", "587"));
        MailSettings startTlsOnSmtps = MailSettings.read(complete().withProperty("regi-volley.mail.port", "465"));
        MailSettings matching = MailSettings.read(complete());

        // Act
        var first = implicitOnSubmission.portWarning();
        var second = startTlsOnSmtps.portWarning();
        var none = matching.portWarning();

        // Assert
        assertThat(first).hasValueSatisfying(text -> assertThat(text).contains("SMTP_PORT", "TLS"));
        assertThat(second).hasValueSatisfying(text -> assertThat(text).contains("SMTP_PORT", "STARTTLS"));
        assertThat(none).isEmpty();
    }
}
