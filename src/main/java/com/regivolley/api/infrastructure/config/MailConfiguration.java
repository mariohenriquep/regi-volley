package com.regivolley.api.infrastructure.config;

import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.infrastructure.notification.LoggingAccountLinkMailer;
import com.regivolley.api.infrastructure.notification.LoggingNotifier;
import com.regivolley.api.infrastructure.notification.MailDelivery;
import com.regivolley.api.infrastructure.notification.MailDispatcher;
import com.regivolley.api.infrastructure.notification.MailLinks;
import com.regivolley.api.infrastructure.notification.MailTemplates;
import com.regivolley.api.infrastructure.notification.SmtpAccountLinkMailer;
import com.regivolley.api.infrastructure.notification.SmtpNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;
import java.util.Properties;

/**
 * Wiring of the two notification ports (issue #40). With an SMTP host configured ({@code SMTP_HOST}) the SMTP adapters are used;
 * without one the logging adapters stay - which only {@code dev} and {@code test} can reach, because {@link StartupGuardConfiguration}
 * refuses to start any other profile without a complete, encrypted mail configuration. Settings that are wrong in themselves (a bad
 * port or security mode, no encryption towards a host that is not this machine) stop the start under every profile rather than quietly
 * sending in clear.
 *
 * <p>The mail server, the sender and the links all come from the environment ({@link MailSettings}); Spring Boot's own
 * {@code spring.mail.*} auto-configuration is not used, so a blank {@code SMTP_HOST} cannot half-enable it.
 */
@Configuration
public class MailConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MailConfiguration.class);
    private static final int ACCOUNT_LINK_QUEUE = 100;
    private static final int NOTICE_QUEUE = 500;
    private static final int CONNECTION_TIMEOUT_MS = 5_000;
    private static final int IO_TIMEOUT_MS = 15_000;

    @Bean
    public MailSettings mailSettings(Environment environment) {
        return MailSettings.read(environment);
    }

    /** Real delivery: a JavaMail sender, templates, an asynchronous dispatcher with retries, and the two adapters on top. */
    @Configuration
    @Conditional(SmtpConfigured.class)
    static class Smtp {

        @Bean
        JavaMailSender mailSender(MailSettings settings) {
            List<String> problems = settings.structuralProblems();
            if (!problems.isEmpty()) {
                throw new IllegalStateException("The mail settings are not usable: " + String.join("; ", problems));
            }
            settings.portWarning().ifPresent(LOG::warn);
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settings.host());
            sender.setPort(settings.port());
            sender.setDefaultEncoding("UTF-8");
            if (!settings.username().isEmpty()) {
                sender.setUsername(settings.username());
                sender.setPassword(settings.password());
            }
            sender.setJavaMailProperties(javaMailProperties(settings));
            return sender;
        }

        @Bean
        MailDelivery mailDelivery(JavaMailSender sender, MailSettings settings) {
            return new MailDelivery(sender, settings.senderAddress());
        }

        @Bean
        MailTemplates mailTemplates() {
            return MailTemplates.load();
        }

        @Bean
        MailLinks mailLinks(MailSettings settings) {
            return new MailLinks(settings.linkOrigin());
        }

        /**
         * Account links (activation, reset) have a queue of their own, so a burst of booking notices (a cancelled session, a no-show
         * round) can never push a link out of the queue, and the other way round.
         */
        @Bean(destroyMethod = "shutdown")
        MailDispatcher accountLinkDispatcher() {
            return MailDispatcher.standard(ACCOUNT_LINK_QUEUE);
        }

        @Bean(destroyMethod = "shutdown")
        MailDispatcher noticeDispatcher() {
            return MailDispatcher.standard(NOTICE_QUEUE);
        }

        @Bean
        AccountLinkMailer accountLinkMailer(@Qualifier("accountLinkDispatcher") MailDispatcher dispatcher, MailDelivery delivery,
                                            MailTemplates templates, MailLinks links) {
            return new SmtpAccountLinkMailer(dispatcher, delivery, templates, links);
        }

        @Bean
        Notifier notifier(@Qualifier("noticeDispatcher") MailDispatcher dispatcher, MailDelivery delivery, MailTemplates templates, MemberRepository members,
                          SessionRepository sessions, AssociationRepository associations, JoinRequestRepository joinRequests) {
            return new SmtpNotifier(dispatcher, delivery, templates, members, sessions, associations, joinRequests);
        }

        /**
         * Encryption is required, not opportunistic: STARTTLS must succeed (an attacker cannot strip it) and the server's certificate
         * must match its host name. Timeouts keep a slow server from holding a sender thread. JavaMail's debug output (which would
         * print the whole conversation, links included) is off.
         */
        static Properties javaMailProperties(MailSettings settings) {
            Properties properties = new Properties();
            properties.setProperty("mail.transport.protocol", "smtp");
            properties.setProperty("mail.debug", "false");
            properties.setProperty("mail.smtp.auth", Boolean.toString(!settings.username().isEmpty()));
            properties.setProperty("mail.smtp.connectiontimeout", Integer.toString(CONNECTION_TIMEOUT_MS));
            properties.setProperty("mail.smtp.timeout", Integer.toString(IO_TIMEOUT_MS));
            properties.setProperty("mail.smtp.writetimeout", Integer.toString(IO_TIMEOUT_MS));
            switch (settings.security()) {
                case STARTTLS -> {
                    properties.setProperty("mail.smtp.starttls.enable", "true");
                    properties.setProperty("mail.smtp.starttls.required", "true");
                    properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
                    properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                }
                case TLS -> {
                    properties.setProperty("mail.smtp.ssl.enable", "true");
                    properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
                    properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                }
                case NONE -> {
                    // A local mail catcher (Mailpit) under dev: no encryption to configure.
                }
            }
            return properties;
        }
    }

    /** No SMTP host: the adapters that only log that a notification or a mail is due (ids and link references, never content). */
    @Configuration
    @Conditional(SmtpNotConfigured.class)
    static class Logging {

        @Bean
        AccountLinkMailer accountLinkMailer() {
            return new LoggingAccountLinkMailer();
        }

        @Bean
        Notifier notifier() {
            return new LoggingNotifier();
        }
    }

    /** Matches when {@code SMTP_HOST} is set. */
    static final class SmtpConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return MailSettings.read(context.getEnvironment()).configured();
        }
    }

    /** Matches when {@code SMTP_HOST} is not set. */
    static final class SmtpNotConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !MailSettings.read(context.getEnvironment()).configured();
        }
    }
}
