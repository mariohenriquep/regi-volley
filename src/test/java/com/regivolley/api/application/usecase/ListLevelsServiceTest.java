package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListLevelsQuery;
import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-03: the administrator reads the association's levels. */
@ExtendWith(MockitoExtension.class)
class ListLevelsServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;

    private Association association;
    private ListLevelsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        useCase = new ListLevelsService(members, associations);
    }

    @Test
    void anAdministratorGetsTheirAssociationWithItsLevelsAndEntryLevel() {
        // Arrange
        Member admin = Data.admin(association);
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(associations.findById(association.id())).thenReturn(Optional.of(association));

        // Act
        Association found = useCase.execute(new ListLevelsQuery(Data.actor(admin)));

        // Assert
        assertThat(found).isSameAs(association);
        assertThat(found.levels()).extracting(level -> level.name()).containsExactly("Beginner", "Intermediate", "Advanced");
        assertThat(found.entryLevelId()).isEqualTo(Data.level(association, "Beginner"));
        verify(associations).findById(association.id());
    }

    @Test
    void aCoachIsNotAllowedAndNothingIsRead() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ListLevelsQuery(Data.actor(coach)));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(associations);
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListLevelsQuery(Data.actor(stranger)));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(associations);
    }

    @Test
    void aMissingAssociationIsNotFound() {
        // Arrange
        Member admin = Data.admin(association);
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(associations.findById(association.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListLevelsQuery(Data.actor(admin)));

        // Act / Assert
        assertThrows(AssociationNotFoundException.class, act);
    }
}
