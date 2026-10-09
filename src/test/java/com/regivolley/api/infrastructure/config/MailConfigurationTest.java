package com.regivolley.api.infrastructure.config;

import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.infrastructure.notification.LoggingAccountLinkMailer;
import com.regivolley.api.infrastructure.notification.LoggingNotifier;
import com.regivolley.api.infrastructure.notification.MailDispatcher;
import com.regivolley.api.infrastructure.notification.SmtpAccountLinkMailer;
import com.regivolley.api.infrastructure.notification.SmtpNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.regivolley.testsupport.SmtpTestServer;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** Issue #40: SMTP adapters when a host is configured, the logging ones otherwise (only reachable under dev / test, see the guard). */
class MailConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(MailConfiguration.class))
            .withBean(MemberRepository.class, () -> mock(MemberRepository.class))
            .withBean(SessionRepository.class, () -> mock(SessionRepository.class))
            .withBean(AssociationRepository.class, () -> mock(AssociationRepository.class))
            .withBean(JoinRequestRepository.class, () -> mock(JoinRequestRepository.class));

    @Test
    void withoutAHostTheLoggingAdaptersStayActive() {
        // Arrange
        ApplicationContextRunner none = runner.withPropertyValues("regi-volley.mail.host=");

        // Act
        none.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Notifier.class)).isInstanceOf(LoggingNotifier.class);
            assertThat(context.getBean(AccountLinkMailer.class)).isInstanceOf(LoggingAccountLinkMailer.class);
            assertThat(context).doesNotHaveBean(MailDispatcher.class);
        });
    }

    @Test
    void withAHostTheSmtpAdaptersReplaceThem() {
        // Arrange
        ApplicationContextRunner smtp = runner.withPropertyValues("regi-volley.mail.host=localhost", "regi-volley.mail.port=1025",
                "regi-volley.mail.security=none", "regi-volley.mail.from=no-reply@example.org");

        // Act
        smtp.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Notifier.class)).isInstanceOf(SmtpNotifier.class);
            assertThat(context.getBean(AccountLinkMailer.class)).isInstanceOf(SmtpAccountLinkMailer.class);
            assertThat(context.getBeansOfType(MailDispatcher.class)).containsOnlyKeys("accountLinkDispatcher", "noticeDispatcher");
        });
    }

    @Test
    void startTlsIsRequiredNotJustOffered() {
        // Arrange
        ApplicationContextRunner starttls = runner.withPropertyValues("regi-volley.mail.host=smtp.example.org",
                "regi-volley.mail.username=mailer", "regi-volley.mail.password=secret-value", "regi-volley.mail.from=no-reply@example.org");

        // Act
        starttls.run(context -> {
            // Assert
            JavaMailSenderImpl sender = (JavaMailSenderImpl) context.getBean(org.springframework.mail.javamail.JavaMailSender.class);
            Properties properties = sender.getJavaMailProperties();
            assertThat(sender.getHost()).isEqualTo("smtp.example.org");
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getUsername()).isEqualTo("mailer");
            assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.ssl.enable")).isNull();
            assertThat(properties.getProperty("mail.debug")).isEqualTo("false");
        });
    }

    @Test
    void implicitTlsEncryptsFromTheFirstByte() {
        // Arrange
        ApplicationContextRunner tls = runner.withPropertyValues("regi-volley.mail.host=smtp.example.org", "regi-volley.mail.port=465",
                "regi-volley.mail.security=tls", "regi-volley.mail.from=no-reply@example.org");

        // Act
        tls.run(context -> {
            // Assert
            JavaMailSenderImpl sender = (JavaMailSenderImpl) context.getBean(org.springframework.mail.javamail.JavaMailSender.class);
            Properties properties = sender.getJavaMailProperties();
            assertThat(properties.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.starttls.enable")).isNull();
            assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("false");
        });
    }

    @Test
    void everyConnectionHasTimeoutsSoASlowServerCannotHoldASenderThreadForEver() {
        // Arrange
        ApplicationContextRunner smtp = runner.withPropertyValues("regi-volley.mail.host=localhost", "regi-volley.mail.security=none",
                "regi-volley.mail.from=no-reply@example.org");

        // Act
        smtp.run(context -> {
            // Assert
            Properties properties = ((JavaMailSenderImpl) context.getBean(org.springframework.mail.javamail.JavaMailSender.class))
                    .getJavaMailProperties();
            assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isNotBlank();
            assertThat(properties.getProperty("mail.smtp.timeout")).isNotBlank();
            assertThat(properties.getProperty("mail.smtp.writetimeout")).isNotBlank();
        });
    }

    @Test
    void aSecurityModeThatNamesNothingStopsTheStartEvenUnderDev() {
        // Arrange
        ApplicationContextRunner typo = runner.withPropertyValues("regi-volley.mail.host=localhost", "regi-volley.mail.security=startls");

        // Act
        typo.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_SECURITY");
        });
    }

    @Test
    void aPortThatIsNotAPortStopsTheStartEvenUnderDev() {
        // Arrange
        ApplicationContextRunner bad = runner.withPropertyValues("regi-volley.mail.host=localhost", "regi-volley.mail.port=abc");

        // Act
        bad.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_PORT");
        });
    }

    @Test
    void startTlsIsNeverDowngradedToPlainTextWhenTheServerDoesNotOfferIt() {
        // Arrange - a server that speaks plain SMTP only, and settings that demand STARTTLS
        SmtpTestServer plainServer = new SmtpTestServer().start();
        try {
            MockEnvironment environment = new MockEnvironment().withProperty("regi-volley.mail.host", "127.0.0.1")
                    .withProperty("regi-volley.mail.port", Integer.toString(plainServer.port()))
                    .withProperty("regi-volley.mail.security", "STARTTLS").withProperty("regi-volley.mail.from", "no-reply@example.org");
            JavaMailSender sender = new MailConfiguration.Smtp().mailSender(MailSettings.read(environment));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@example.org");
            message.setTo("ana@example.com");
            message.setSubject("Subject");
            message.setText("Text with a secret link");
            Executable act = () -> sender.send(message);

            // Act
            assertThrows(MailException.class, act);

            // Assert - nothing went over the wire
            assertThat(plainServer.received()).isEmpty();
        } finally {
            plainServer.stop();
        }
    }

    @Test
    void unencryptedMailToAHostThatIsNotLocalStopsTheStartUnderEveryProfile() {
        // Arrange
        ApplicationContextRunner remote = runner.withPropertyValues("regi-volley.mail.host=smtp.example.org", "regi-volley.mail.security=none",
                "spring.profiles.active=dev");

        // Act
        remote.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("SMTP_SECURITY");
        });
    }
}
