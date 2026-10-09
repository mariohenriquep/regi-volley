package com.regivolley.api.infrastructure.config;

import com.regivolley.api.application.command.PurgeStalePendingJoinRequestsCommand;
import com.regivolley.api.application.result.JoinRequestPurgeReport;
import com.regivolley.api.application.usecase.PurgeStalePendingJoinRequestsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JoinRequestPurgeSchedulerTest {

    @Mock
    private PurgeStalePendingJoinRequestsUseCase purge;

    @Test
    void runsThePurge() {
        // Arrange
        when(purge.execute(new PurgeStalePendingJoinRequestsCommand())).thenReturn(new JoinRequestPurgeReport(2, 0, 5));
        JoinRequestPurgeScheduler scheduler = new JoinRequestPurgeScheduler(purge);

        // Act
        scheduler.purgeStaleJoinRequests();

        // Assert
        verify(purge).execute(new PurgeStalePendingJoinRequestsCommand());
    }

    @Test
    void aFailingRunNeverEscapesIntoTheSchedulerThread() {
        // Arrange
        when(purge.execute(new PurgeStalePendingJoinRequestsCommand())).thenThrow(new IllegalStateException("db down"));
        JoinRequestPurgeScheduler scheduler = new JoinRequestPurgeScheduler(purge);

        // Act
        scheduler.purgeStaleJoinRequests();

        // Assert
        verify(purge).execute(new PurgeStalePendingJoinRequestsCommand());
    }

    @Test
    void isScheduledDailyInLisbonTimeAndSwitchedByAProperty() throws NoSuchMethodException {
        // Arrange
        Method run = JoinRequestPurgeScheduler.class.getMethod("purgeStaleJoinRequests");

        // Act
        Scheduled scheduled = run.getAnnotation(Scheduled.class);
        ConditionalOnProperty condition = JoinRequestPurgeScheduler.class.getAnnotation(ConditionalOnProperty.class);

        // Assert
        assertThat(scheduled.zone()).isEqualTo("Europe/Lisbon");
        assertThat(scheduled.cron()).contains("0 30 3 * * *");
        assertThat(condition.name()).containsExactly("regi-volley.join-request-purge.enabled");
    }
}
