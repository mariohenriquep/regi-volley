package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.BackgroundWork.Lane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Threat model D-10 and the lanes of issue #38: administrator resends can fill their own lane but never take the password-reset lane's capacity. */
class ExecutorBackgroundWorkTest {

    private ExecutorBackgroundWork work;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        work = new ExecutorBackgroundWork();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        work.shutdown();
    }

    private Runnable blocked() {
        return () -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    @Test
    void aSaturatedResendLaneStillLeavesTheAccountMailLaneFree() throws Exception {
        // Arrange - far more resends than the lane can hold: the surplus is dropped, never run on the caller
        AtomicInteger ranOnCaller = new AtomicInteger();
        Thread caller = Thread.currentThread();
        for (int i = 0; i < 500; i++) {
            work.run(Lane.ADMIN_RESEND, () -> {
                if (Thread.currentThread() == caller) {
                    ranOnCaller.incrementAndGet();
                }
                blocked().run();
            });
        }
        CountDownLatch resetSent = new CountDownLatch(1);

        // Act
        work.run(Lane.ACCOUNT_MAIL, resetSent::countDown);

        // Assert
        assertThat(resetSent.await(5, TimeUnit.SECONDS)).as("the password-reset lane still runs its work").isTrue();
        assertThat(ranOnCaller).hasValue(0);
    }

    @Test
    void aFullResendLaneDropsTheWorkWithoutThrowing() {
        // Arrange
        for (int i = 0; i < 100; i++) {
            work.run(Lane.ADMIN_RESEND, blocked());
        }
        AtomicInteger ran = new AtomicInteger();

        // Act
        work.run(Lane.ADMIN_RESEND, ran::incrementAndGet);

        // Assert
        assertThat(ran).hasValue(0);
    }

    @Test
    void aSaturatedAccountMailLaneDoesNotTakeTheResendLane() throws Exception {
        // Arrange
        for (int i = 0; i < 500; i++) {
            work.run(Lane.ACCOUNT_MAIL, blocked());
        }
        CountDownLatch resent = new CountDownLatch(1);

        // Act
        work.run(Lane.ADMIN_RESEND, resent::countDown);

        // Assert
        assertThat(resent.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
