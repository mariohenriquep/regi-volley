package com.regivolley.api.infrastructure.config;

import com.regivolley.api.application.command.GenerateSessionsForAllAssociationsCommand;
import com.regivolley.api.application.result.GenerationRunReport;
import com.regivolley.api.application.usecase.GenerateSessionsForAllAssociationsUseCase;
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
class SessionGenerationSchedulerTest {

    @Mock
    private GenerateSessionsForAllAssociationsUseCase generateAll;

    @Test
    void runsTheAllAssociationsGeneration() {
        // Arrange
        when(generateAll.execute(new GenerateSessionsForAllAssociationsCommand()))
                .thenReturn(new GenerationRunReport(2, 0, 8));
        SessionGenerationScheduler scheduler = new SessionGenerationScheduler(generateAll);

        // Act
        scheduler.generateSessions();

        // Assert
        verify(generateAll).execute(new GenerateSessionsForAllAssociationsCommand());
    }

    @Test
    void aFailingRunNeverEscapesIntoTheSchedulerThread() {
        // Arrange
        when(generateAll.execute(new GenerateSessionsForAllAssociationsCommand())).thenThrow(new IllegalStateException("db down"));
        SessionGenerationScheduler scheduler = new SessionGenerationScheduler(generateAll);

        // Act
        scheduler.generateSessions();

        // Assert
        verify(generateAll).execute(new GenerateSessionsForAllAssociationsCommand());
    }

    @Test
    void isScheduledDailyInLisbonTimeAndSwitchedByAProperty() throws NoSuchMethodException {
        // Arrange
        Method run = SessionGenerationScheduler.class.getMethod("generateSessions");

        // Act
        Scheduled scheduled = run.getAnnotation(Scheduled.class);
        ConditionalOnProperty condition = SessionGenerationScheduler.class.getAnnotation(ConditionalOnProperty.class);

        // Assert
        assertThat(scheduled.zone()).isEqualTo("Europe/Lisbon");
        assertThat(scheduled.cron()).contains("0 0 3 * * *");
        assertThat(condition.name()).containsExactly("regi-volley.session-generation.enabled");
    }
}
