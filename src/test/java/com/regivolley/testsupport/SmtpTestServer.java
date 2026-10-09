package com.regivolley.testsupport;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An in-process SMTP server for the mail tests (GreenMail): nothing leaves the machine, whatever the test does. Start it in
 * {@code @BeforeEach}, {@link #stop()} it in {@code @AfterEach}, send through {@link #sender()}, read with {@link #awaitMessages}.
 */
public final class SmtpTestServer {

    private final GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    /** What one received message looked like, decoded. */
    public record Received(String from, List<String> to, String subject, String text, String html, MimeMessage raw) {

        public String header(String name) {
            try {
                String[] values = raw.getHeader(name);
                return values == null ? null : values[0];
            } catch (MessagingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public SmtpTestServer start() {
        greenMail.start();
        return this;
    }

    public void stop() {
        greenMail.stop();
    }

    public int port() {
        return greenMail.getSmtp().getPort();
    }

    /** A sender pointed at this server, without authentication or encryption. */
    public JavaMailSenderImpl sender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port());
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }

    /** Waits for {@code count} messages (and no more than that arrive in the next moment) and decodes them. */
    public List<Received> awaitMessages(int count) {
        boolean arrived = greenMail.waitForIncomingEmail(TimeUnit.SECONDS.toMillis(10), count);
        if (!arrived) {
            throw new AssertionError("Expected " + count + " message(s) but got " + greenMail.getReceivedMessages().length);
        }
        return received();
    }

    /** Whether nothing arrives within the given time: for "this must not be sent" checks. */
    public boolean staysEmptyFor(long millis) {
        return !greenMail.waitForIncomingEmail(millis, 1);
    }

    public List<Received> received() {
        return Arrays.stream(greenMail.getReceivedMessages()).map(SmtpTestServer::decode).toList();
    }

    private static Received decode(MimeMessage message) {
        try {
            String[] parts = new String[2];
            collect(message, parts);
            List<String> to = Arrays.stream(message.getRecipients(Message.RecipientType.TO)).map(Object::toString).toList();
            return new Received(message.getFrom()[0].toString(), to, message.getSubject(), parts[0], parts[1], message);
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void collect(Part part, String[] texts) throws MessagingException, IOException {
        if (part.isMimeType("text/plain") && texts[0] == null) {
            texts[0] = (String) part.getContent();
        } else if (part.isMimeType("text/html") && texts[1] == null) {
            texts[1] = (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, texts);
            }
        }
    }
}
