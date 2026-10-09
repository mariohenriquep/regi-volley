package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.testsupport.LogCapture;
import com.regivolley.testsupport.SmtpTestServer;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Issue #40, threat model D-11 / G3: the activation and reset links reach the account's mailbox over a real SMTP conversation. */
class SmtpAccountLinkMailerTest {

    private static final String TOKEN = "Tk3-abcDEF_0123456789abcdefghijklmnopqrstuvw";
    private static final String ORIGIN = "https://app.example.org";

    private SmtpTestServer smtp;
    private MailDispatcher dispatcher;
    private SmtpAccountLinkMailer mailer;

    @BeforeEach
    void start() {
        smtp = new SmtpTestServer().start();
        dispatcher = new MailDispatcher(1, 20, List.of(Duration.ofMillis(10), Duration.ofMillis(10)));
        mailer = mailerOver(smtp.sender());
    }

    @AfterEach
    void stop() {
        dispatcher.shutdown();
        smtp.stop();
    }

    private SmtpAccountLinkMailer mailerOver(JavaMailSenderImpl sender) {
        return new SmtpAccountLinkMailer(dispatcher, new MailDelivery(sender, "RegiVolley <no-reply@example.org>"),
                MailTemplates.load(), new MailLinks(ORIGIN));
    }

    private static AccountLink link(String token, String expiresAt) {
        return new AccountLink(UUID.randomUUID(), token, Instant.parse(expiresAt));
    }

    @Test
    void mailsTheActivationLinkToTheAccountAddress() {
        // Arrange
        AccountLink link = link(TOKEN, "2026-10-19T10:00:00Z");

        // Act
        mailer.send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link);

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.to()).containsExactly("ana.silva@example.com");
        assertThat(mail.from()).isEqualTo("RegiVolley <no-reply@example.org>");
        assertThat(mail.subject()).isEqualTo("Activate your RegiVolley account");
        assertThat(mail.text()).contains(ORIGIN + "/activate#token=" + TOKEN).contains("19/10/2026 11:00");
        assertThat(mail.html()).contains("href=\"" + ORIGIN + "/activate#token=" + TOKEN + "\"");
        assertThat(mail.header("Auto-Submitted")).isEqualTo("auto-generated");
    }

    @Test
    void mailsThePasswordResetLinkOnItsOwnFrontendRoute() {
        // Arrange
        AccountLink link = link(TOKEN, "2026-11-20T10:30:00Z");

        // Act
        mailer.send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.PASSWORD_RESET, link);

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.subject()).isEqualTo("Reset your RegiVolley password");
        assertThat(mail.text()).contains(ORIGIN + "/reset-password#token=" + TOKEN).contains("20/11/2026 10:30");
        assertThat(mail.html()).contains(ORIGIN + "/reset-password#token=" + TOKEN);
    }

    @Test
    void theTokenIsUrlEncodedSoNothingInItCanEscapeTheLink() {
        // Arrange
        AccountLink link = link("a+b/c=d&e f\"<g>", "2026-10-19T10:00:00Z");

        // Act
        mailer.send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link);

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.text()).contains(ORIGIN + "/activate#token=a%2Bb%2Fc%3Dd%26e%20f%22%3Cg%3E");
        assertThat(mail.html()).doesNotContain("<g>");
    }

    @Test
    void theLinkBuilderRequiresTheOriginToBeNormalisedAlready() {
        // Arrange
        Executable act = () -> new MailLinks(ORIGIN + "/");

        // Act
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(refused.getMessage()).contains("slash");
    }

    @Test
    void aServerThatRefusesTheAddressForGoodIsAskedOnlyOnce() throws Exception {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        JavaMailSenderImpl refusing = new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
                calls.incrementAndGet();
                throw new MailSendException("failed", new SMTPAddressFailedException(address("ana.silva@example.com"), "RCPT TO", 550, "no such user"));
            }
        };

        // Act
        mailerOver(refusing).send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link(TOKEN, "2026-10-19T10:00:00Z"));

        // Assert
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(calls).hasValue(1);
    }

    private static InternetAddress address(String text) {
        try {
            return new InternetAddress(text);
        } catch (AddressException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aTransientFailureIsRetriedAndTheLinkStillArrivesOnce() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        JavaMailSenderImpl flaky = new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
                if (calls.incrementAndGet() == 1) {
                    throw new MailSendException("451 try again later for ana.silva@example.com");
                }
                super.doSend(messages, originalMessages);
            }
        };
        flaky.setHost("127.0.0.1");
        flaky.setPort(smtp.port());

        // Act
        mailerOver(flaky).send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link(TOKEN, "2026-10-19T10:00:00Z"));

        // Assert
        assertThat(smtp.awaitMessages(1)).hasSize(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void neitherTheTokenNorTheAddressNorTheBodyIsEverLogged() throws Exception {
        // Arrange
        JavaMailSenderImpl failing = new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
                throw new MailSendException("550 5.1.1 <ana.silva@example.com> rejected: " + TOKEN);
            }
        };
        try (LogCapture logs = new LogCapture()) {
            // Act
            SmtpAccountLinkMailer failingMailer = mailerOver(failing);
            failingMailer.send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link(TOKEN, "2026-10-19T10:00:00Z"));
            mailer.send(EmailAddress.of("rita@example.com"), AccountLinkPurpose.PASSWORD_RESET, link(TOKEN, "2026-10-19T10:00:00Z"));
            smtp.awaitMessages(1);
            assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();

            // Assert
            assertThat(logs.everything()).contains("MailSendException")
                    .doesNotContain(TOKEN).doesNotContain("ana.silva").doesNotContain("rita@example.com")
                    .doesNotContain("Choose a new password").doesNotContain("token=");
        }
    }

    @Test
    void theLinkIsDescribedInTheLogByItsReferenceOnly() throws Exception {
        // Arrange
        AccountLink link = link(TOKEN, "2026-10-19T10:00:00Z");
        JavaMailSenderImpl failing = new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
                throw new MailSendException("down");
            }
        };
        try (LogCapture logs = new LogCapture()) {
            // Act
            mailerOver(failing).send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link);
            assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();

            // Assert
            assertThat(logs.everything()).contains(link.reference().toString(), "ACTIVATION");
        }
    }
}
