package com.regivolley.api.infrastructure.notification;

import ch.qos.logback.classic.Level;
import com.regivolley.testsupport.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Issue #40: mail is sent off the request thread, on a bounded pool, with a bounded number of retries and logs that name ids only. */
class MailDispatcherTest {

    private static final List<Duration> FAST_RETRIES = List.of(Duration.ofMillis(10), Duration.ofMillis(10));

    private LogCapture logs;
    private MailDispatcher dispatcher;

    @BeforeEach
    void attach() {
        logs = new LogCapture();
    }

    @AfterEach
    void detach() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
        logs.close();
    }

    private List<String> messages() {
        return logs.events().stream().map(event -> event.getFormattedMessage()).toList();
    }

    @Test
    void runsTheTaskOffTheCallingThread() throws Exception {
        // Arrange
        dispatcher = new MailDispatcher(1, 10, FAST_RETRIES);
        CountDownLatch done = new CountDownLatch(1);
        Thread[] ranOn = new Thread[1];

        // Act
        dispatcher.submit("welcome (member=1)", () -> {
            ranOn[0] = Thread.currentThread();
            done.countDown();
        });

        // Assert
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ranOn[0]).isNotSameAs(Thread.currentThread());
        assertThat(ranOn[0].isDaemon()).isTrue();
    }

    @Test
    void aFailingTaskIsRetriedUntilItSucceeds() throws Exception {
        // Arrange
        dispatcher = new MailDispatcher(1, 10, FAST_RETRIES);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        // Act
        dispatcher.submit("promotion (member=1)", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("550 rita@example.com rejected");
            }
            done.countDown();
        });

        // Assert
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(attempts).hasValue(3);
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(logs.events()).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void givesUpAfterTheLastRetryAndLogsTheIdsAndTheExceptionClassOnly() throws Exception {
        // Arrange
        dispatcher = new MailDispatcher(1, 10, FAST_RETRIES);
        AtomicInteger attempts = new AtomicInteger();

        // Act
        dispatcher.submit("promotion (association=A, member=M)", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("550 rita@example.com rejected, token=secret-token");
        });

        // Assert
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(attempts).hasValue(3);
        assertThat(messages()).anySatisfy(message -> assertThat(message)
                .contains("promotion (association=A, member=M)", "IllegalStateException", "3"));
        assertThat(logs.everything()).doesNotContain("rita@example.com").doesNotContain("secret-token");
        assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    void aPermanentFailureIsNotRetried() throws Exception {
        // Arrange
        dispatcher = new MailDispatcher(1, 10, FAST_RETRIES);
        AtomicInteger attempts = new AtomicInteger();

        // Act
        dispatcher.submit("rejection (request=R)", () -> {
            attempts.incrementAndGet();
            throw new PermanentMailFailure(new IllegalArgumentException("550 rita@example.com no such user"));
        });

        // Assert
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(attempts).hasValue(1);
        assertThat(messages()).anySatisfy(message -> assertThat(message).contains("rejection (request=R)", "IllegalArgumentException", "permanent"));
        assertThat(logs.everything()).doesNotContain("rita@example.com");
    }

    @Test
    void anErrorNeitherLeaksTheSlotNorStopsLaterMail() throws Exception {
        // Arrange - one slot only: if the failed task kept it, the next submit would be dropped
        dispatcher = new MailDispatcher(1, 1, FAST_RETRIES);

        // Act
        dispatcher.submit("broken (member=M)", () -> {
            throw new LinkageError("class initialisation failed");
        });

        // Assert
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        CountDownLatch next = new CountDownLatch(1);
        assertThat(dispatcher.submit("next (member=M)", next::countDown)).isTrue();
        assertThat(next.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(messages()).anySatisfy(message -> assertThat(message).contains("broken (member=M)", "LinkageError"));
    }

    @Test
    void aWaitingRetryDoesNotHoldTheWorkerSoOtherMailGoesFirst() throws Exception {
        // Arrange - one worker; the second task is queued from inside the first attempt, before its retry exists, so the order is fixed
        dispatcher = new MailDispatcher(1, 10, List.of(Duration.ofMillis(50)));
        List<String> order = new CopyOnWriteArrayList<>();
        AtomicInteger firstAttempts = new AtomicInteger();

        // Act
        dispatcher.submit("first", () -> {
            if (firstAttempts.incrementAndGet() == 1) {
                order.add("first#1");
                dispatcher.submit("second", () -> order.add("second"));
                throw new IllegalStateException("fail once");
            }
            order.add("first#2");
        });

        // Assert
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(order).containsExactly("first#1", "second", "first#2");
    }

    @Test
    void dropsAndLogsWorkBeyondTheBound() throws Exception {
        // Arrange
        dispatcher = new MailDispatcher(1, 2, FAST_RETRIES);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();
        dispatcher.submit("blocker", () -> release.await());
        dispatcher.submit("queued", ran::incrementAndGet);

        // Act
        boolean accepted = dispatcher.submit("overflow (member=M)", ran::incrementAndGet);
        release.countDown();

        // Assert
        assertThat(accepted).isFalse();
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(ran).hasValue(1);
        assertThat(messages()).anySatisfy(message -> assertThat(message).contains("overflow (member=M)", "queue is full"));
    }

    @Test
    void nothingIsAcceptedAfterShutdown() {
        // Arrange
        dispatcher = new MailDispatcher(1, 10, FAST_RETRIES);
        dispatcher.shutdown();

        // Act
        boolean accepted = dispatcher.submit("late (member=M)", () -> { });

        // Assert
        assertThat(accepted).isFalse();
        assertThat(messages()).anySatisfy(message -> assertThat(message).contains("late (member=M)"));
    }
}
