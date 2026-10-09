package com.regivolley.api.infrastructure.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAccountLinkMailerTest {

    private final LoggingAccountLinkMailer mailer = new LoggingAccountLinkMailer();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAccountLinkMailer.class);

    @BeforeEach
    void attach() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logs);
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsTheLinkByReferenceAndNeverTheTokenNorTheAddress() {
        // Arrange
        UUID reference = UUID.randomUUID();
        AccountLink link = new AccountLink(reference, "raw-token-0123456789", Instant.parse("2026-10-19T09:00:00Z"));

        // Act
        mailer.send(EmailAddress.of("ana.silva@example.com"), AccountLinkPurpose.ACTIVATION, link);

        // Assert
        assertThat(messages()).singleElement().satisfies(message -> {
            assertThat(message).contains(reference.toString(), "ACTIVATION");
            assertThat(message).doesNotContain("raw-token-0123456789").doesNotContain("ana.silva").doesNotContain("example.com");
        });
    }
}
