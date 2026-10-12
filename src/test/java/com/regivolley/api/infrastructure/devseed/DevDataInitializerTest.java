package com.regivolley.api.infrastructure.devseed;

import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.testsupport.LogCapture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class DevDataInitializerTest {

    private final AssociationRepository associations = mock(AssociationRepository.class);
    private final DevDataSeeder seeder = mock(DevDataSeeder.class);
    private final DevDataInitializer initializer = new DevDataInitializer(associations, seeder);

    @Test
    void seedsWhenTheDevAssociationDoesNotExist() {
        // Arrange
        when(associations.existsByShortName(ShortName.of(DevDataSeeder.SHORT_NAME))).thenReturn(false);

        // Act
        initializer.run(null);

        // Assert
        verify(seeder).seed();
    }

    @Test
    void doesNothingWhenTheDevAssociationAlreadyExists() {
        // Arrange
        when(associations.existsByShortName(ShortName.of(DevDataSeeder.SHORT_NAME))).thenReturn(true);

        // Act
        initializer.run(null);

        // Assert
        verify(seeder, never()).seed();
    }

    @Test
    void aFailingSeedDoesNotStopTheApplicationAndLogsOnlyTheExceptionClass() {
        // Arrange
        when(associations.existsByShortName(any())).thenReturn(false);
        doThrow(new IllegalStateException("admin@volei-lisboa.test is taken")).when(seeder).seed();

        // Act
        String logged;
        try (LogCapture log = new LogCapture()) {
            initializer.run(null);
            logged = log.everything();
        }

        // Assert
        verify(seeder).seed();
        assertThat(logged).contains("IllegalStateException").doesNotContain("admin@volei-lisboa.test");
    }
}
