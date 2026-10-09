package com.regivolley.api.infrastructure.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Hands one rendered message to the SMTP server: a UTF-8 multipart/alternative (plain text and HTML) from the configured sender
 * address, marked {@code Auto-Submitted: auto-generated} so that auto-responders stay quiet. The recipient is parsed as exactly one
 * mailbox, so an address such as {@code a,b@example.org} can never add a second recipient. Logs nothing: the {@link MailDispatcher}
 * decides about retries and logs by ids.
 *
 * <p>Failures that cannot be cured by trying again - an address that is not an address, a 5xx refusal from the server - are thrown as
 * {@link PermanentMailFailure}; everything else (a dead server, a timeout, a 4xx "try later") is thrown as it came and retried.
 */
public final class MailDelivery {

    private static final int MAX_CAUSE_DEPTH = 10;

    private final JavaMailSender sender;
    private final String from;

    public MailDelivery(JavaMailSender sender, String from) {
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.from = Objects.requireNonNull(from, "from must not be null");
    }

    void deliver(String recipient, MailContent content) throws MessagingException {
        InternetAddress to = parse(recipient);
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(content.subject());
        helper.setText(content.text(), content.html());
        message.setHeader("Auto-Submitted", "auto-generated");
        try {
            sender.send(message);
        } catch (MailException failure) {
            if (isPermanent(failure, 0)) {
                throw new PermanentMailFailure(failure);
            }
            throw failure;
        }
    }

    private static InternetAddress parse(String recipient) {
        try {
            InternetAddress to = new InternetAddress(recipient, true);
            to.validate();
            return to;
        } catch (AddressException e) {
            throw new PermanentMailFailure(e);
        }
    }

    /** True when the failure, or anything it wraps, is an SMTP 5xx reply: the server said no for good. */
    private static boolean isPermanent(Throwable failure, int depth) {
        if (failure == null || depth > MAX_CAUSE_DEPTH) {
            return false;
        }
        if (failure instanceof SMTPSendFailedException refused && isPermanentReply(refused.getReturnCode())) {
            return true;
        }
        if (failure instanceof SMTPAddressFailedException refused && isPermanentReply(refused.getReturnCode())) {
            return true;
        }
        if (failure instanceof MailSendException sendFailure) {
            for (Exception perMessage : sendFailure.getMessageExceptions()) {
                if (isPermanent(perMessage, depth + 1)) {
                    return true;
                }
            }
        }
        if (failure instanceof MessagingException messaging && isPermanent(messaging.getNextException(), depth + 1)) {
            return true;
        }
        return isPermanent(failure.getCause(), depth + 1);
    }

    private static boolean isPermanentReply(int code) {
        return code >= 500 && code <= 599;
    }
}
