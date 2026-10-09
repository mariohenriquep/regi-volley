package com.regivolley.api.infrastructure.notification;

import com.regivolley.testsupport.SmtpTestServer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Issue #40: one well-formed multipart message to exactly one mailbox, over a real SMTP conversation. */
class MailDeliveryTest {

    private static final MailContent CONTENT = new MailContent("Plain subject", "Plain text body", "<p>Html body</p>");

    private SmtpTestServer smtp;
    private MailDelivery delivery;

    @BeforeEach
    void start() {
        smtp = new SmtpTestServer().start();
        delivery = new MailDelivery(smtp.sender(), "RegiVolley <no-reply@example.org>");
    }

    @AfterEach
    void stop() {
        smtp.stop();
    }

    @Test
    void sendsOneMessageWithBothBodiesFromTheConfiguredSender() throws Exception {
        // Arrange
        String recipient = "ana.silva@example.com";

        // Act
        delivery.deliver(recipient, CONTENT);

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.to()).containsExactly(recipient);
        assertThat(mail.from()).isEqualTo("RegiVolley <no-reply@example.org>");
        assertThat(mail.subject()).isEqualTo("Plain subject");
        assertThat(mail.text()).contains("Plain text body");
        assertThat(mail.html()).contains("<p>Html body</p>");
        assertThat(mail.header("Auto-Submitted")).isEqualTo("auto-generated");
    }

    @Test
    void anAddressThatWouldAddASecondRecipientIsRefusedAndNothingIsSent() {
        // Arrange
        Executable act = () -> delivery.deliver("a,b@example.org", CONTENT);

        // Act
        PermanentMailFailure failure = assertThrows(PermanentMailFailure.class, act);

        // Assert
        assertThat(failure.getCause()).isInstanceOf(AddressException.class);
        assertThat(smtp.staysEmptyFor(300)).isTrue();
    }

    @Test
    void anAddressWithAHeaderInjectionIsRefusedAndNothingIsSent() {
        // Arrange
        Executable act = () -> delivery.deliver("a@example.org\r\nBcc: evil@example.org", CONTENT);

        // Act
        assertThrows(PermanentMailFailure.class, act);

        // Assert
        assertThat(smtp.staysEmptyFor(300)).isTrue();
    }

    @Test
    void aServerRefusingTheRecipientForGoodIsPermanent() {
        // Arrange
        MailDelivery refused = new MailDelivery(failingWith(new MailSendException("failed",
                new SMTPAddressFailedException(address("ana@example.com"), "RCPT TO", 550, "no such user"))), "no-reply@example.org");
        Executable act = () -> refused.deliver("ana@example.com", CONTENT);

        // Act
        PermanentMailFailure failure = assertThrows(PermanentMailFailure.class, act);

        // Assert
        assertThat(failure.reason()).isEqualTo("MailSendException");
    }

    @Test
    void aServerRejectingTheMessageWith5xxIsPermanentEvenWhenWrappedPerMessage() {
        // Arrange
        SMTPSendFailedException rejected = new SMTPSendFailedException("DATA", 554, "rejected as spam", null, null, null, null);
        Map<Object, Exception> failedMessages = new HashMap<>();
        failedMessages.put("message", rejected);
        MailSendException wrapped = new MailSendException(failedMessages);
        MailDelivery delivery = new MailDelivery(failingWith(wrapped), "no-reply@example.org");
        Executable act = () -> delivery.deliver("ana@example.com", CONTENT);

        // Act
        assertThrows(PermanentMailFailure.class, act);

        // Assert - no exception other than the permanent one
        assertThat(wrapped.getMessageExceptions()).containsExactly(rejected);
    }

    @Test
    void aTemporaryRefusalWith4xxOrADeadServerIsLeftToTheRetries() {
        // Arrange
        SMTPSendFailedException busy = new SMTPSendFailedException("RCPT TO", 451, "try again later", null, null, null, null);
        MailDelivery temporary = new MailDelivery(failingWith(new MailSendException("failed", busy)), "no-reply@example.org");
        MailDelivery down = new MailDelivery(failingWith(new MailSendException("Mail server connection failed",
                new MessagingException("Connection refused"))), "no-reply@example.org");

        // Act
        MailSendException first = assertThrows(MailSendException.class, () -> temporary.deliver("ana@example.com", CONTENT));
        MailSendException second = assertThrows(MailSendException.class, () -> down.deliver("ana@example.com", CONTENT));

        // Assert
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
    }

    private static InternetAddress address(String text) {
        try {
            return new InternetAddress(text);
        } catch (AddressException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JavaMailSenderImpl failingWith(MailSendException failure) {
        return new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
                throw failure;
            }
        };
    }

    @Test
    void nonAsciiTextSurvivesTheJourney() throws Exception {
        // Arrange
        MailContent accented = new MailContent("Reservation", "Sessão de sábado — João", "<p>Sessão</p>");

        // Act
        delivery.deliver("ana@example.com", accented);

        // Assert
        assertThat(smtp.awaitMessages(1).get(0).text()).contains("Sessão de sábado — João");
    }
}
