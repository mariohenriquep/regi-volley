package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.command.GenerateSessionsForAllAssociationsCommand;
import com.regivolley.api.application.result.GenerationRunReport;
import com.regivolley.api.application.result.SessionGenerationReport;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.repository.AssociationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateSessionsForAllAssociationsServiceTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private GenerateSessionsUseCase generateOne;

    @Test
    void generatesForEveryAssociationAndTotalsTheSessions() {
        // Arrange
        AssociationId first = AssociationId.generate();
        AssociationId second = AssociationId.generate();
        when(associations.findAllIds()).thenReturn(List.of(first, second));
        when(generateOne.execute(new GenerateSessionsCommand(first))).thenReturn(new SessionGenerationReport(4, List.of()));
        when(generateOne.execute(new GenerateSessionsCommand(second))).thenReturn(new SessionGenerationReport(8, List.of()));
        GenerateSessionsForAllAssociationsUseCase useCase =
                new GenerateSessionsForAllAssociationsService(associations, generateOne);

        // Act
        GenerationRunReport report = useCase.execute(new GenerateSessionsForAllAssociationsCommand());

        // Assert
        assertThat(report).isEqualTo(new GenerationRunReport(2, 0, 12));
    }

    @Test
    void oneFailingAssociationNeverStopsTheOthers() {
        // Arrange
        AssociationId broken = AssociationId.generate();
        AssociationId healthy = AssociationId.generate();
        when(associations.findAllIds()).thenReturn(List.of(broken, healthy));
        when(generateOne.execute(new GenerateSessionsCommand(broken))).thenThrow(new IllegalStateException("boom"));
        when(generateOne.execute(new GenerateSessionsCommand(healthy))).thenReturn(new SessionGenerationReport(4, List.of()));
        GenerateSessionsForAllAssociationsUseCase useCase =
                new GenerateSessionsForAllAssociationsService(associations, generateOne);

        // Act
        GenerationRunReport report = useCase.execute(new GenerateSessionsForAllAssociationsCommand());

        // Assert
        assertThat(report).isEqualTo(new GenerationRunReport(1, 1, 4));
        verify(generateOne).execute(new GenerateSessionsCommand(healthy));
    }

    @Test
    void doesNothingWithoutAssociations() {
        // Arrange
        when(associations.findAllIds()).thenReturn(List.of());
        GenerateSessionsForAllAssociationsUseCase useCase =
                new GenerateSessionsForAllAssociationsService(associations, generateOne);

        // Act
        GenerationRunReport report = useCase.execute(new GenerateSessionsForAllAssociationsCommand());

        // Assert
        assertThat(report).isEqualTo(new GenerationRunReport(0, 0, 0));
    }
}
