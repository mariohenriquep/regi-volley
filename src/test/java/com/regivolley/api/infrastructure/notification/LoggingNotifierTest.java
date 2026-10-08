package com.regivolley.api.infrastructure.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingNotifierTest {

    private final LoggingNotifier notifier = new LoggingNotifier();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingNotifier.class);

    private final AssociationId association = AssociationId.generate();
    private final SessionId session = SessionId.generate();
    private final MemberId member = MemberId.generate();

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
    void logsThePromotionWithIdsOnly() {
        // Arrange
        // (ids from the fields)

        // Act
        notifier.bookingPromoted(association, session, member);

        // Assert
        assertThat(messages()).singleElement().satisfies(message ->
                assertThat(message).contains(association.toString(), session.toString(), member.toString()));
    }

    @Test
    void neverLogsTheFreeTextReasonOfASessionCancellation() {
        // Arrange
        String reason = "Coach Ana Silva is ill, call 912345678";

        // Act
        notifier.sessionCancelled(association, session, member, reason);

        // Assert
        assertThat(messages()).singleElement().satisfies(message -> {
            assertThat(message).contains(session.toString(), member.toString());
            assertThat(message).doesNotContain("Ana").doesNotContain("912345678");
        });
    }

    @Test
    void logsTheNoShowWarningWithTheCount() {
        // Arrange
        // (ids from the fields)

        // Act
        notifier.noShowLimitReached(association, member, 3);

        // Assert
        assertThat(messages()).singleElement().satisfies(message ->
                assertThat(message).contains(member.toString(), "noShows=3"));
    }

    @Test
    void logsTheApprovalWithIdsOnly() {
        // Arrange
        // (ids from the fields)

        // Act
        notifier.memberApproved(association, member);

        // Assert
        assertThat(messages()).singleElement().satisfies(message ->
                assertThat(message).contains(association.toString(), member.toString()));
    }

    @Test
    void logsTheRejectionWithIdsOnly() {
        // Arrange
        JoinRequestId request = JoinRequestId.generate();

        // Act
        notifier.joinRequestRejected(association, request);

        // Assert
        assertThat(messages()).singleElement().satisfies(message ->
                assertThat(message).contains(association.toString(), request.toString()));
    }
}
