package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListTrainingGroupsQuery;
import com.regivolley.api.application.result.TrainingGroupListEntry;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-09: the administrator's group list with levels, venue and coach named, archived groups only on request. */
@ExtendWith(MockitoExtension.class)
class ListTrainingGroupsServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private TrainingGroupRepository groups;
    @Mock
    private VenueRepository venues;

    private Association association;
    private Member admin;
    private Member coach;
    private Venue venue;
    private ListTrainingGroupsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        venue = Data.venue(association, "Pavilion");
        useCase = new ListTrainingGroupsService(members, associations, groups, venues);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(venues.findAllByAssociation(association.id())).thenReturn(List.of(venue));
        lenient().when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(coach));
    }

    private TrainingGroup group(String name, String... levels) {
        return Data.groupAt(association, coach, venue, levels).rename(name);
    }

    @Test
    void activeGroupsAreListedWithTheirLevelsVenueAndCoachNamed() {
        // Arrange
        TrainingGroup wednesday = group("Wednesday", "Advanced", "Beginner");
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(wednesday));

        // Act
        List<TrainingGroupListEntry> entries = useCase.execute(new ListTrainingGroupsQuery(Data.actor(admin), false));

        // Assert
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.group()).isEqualTo(wednesday);
            assertThat(entry.acceptedLevels()).extracting(Level::name).containsExactly("Beginner", "Advanced");
            assertThat(entry.venueName()).contains("Pavilion");
            assertThat(entry.coachName()).contains(coach.name());
        });
    }

    @Test
    void archivedGroupsAreLeftOutUnlessAsked() {
        // Arrange
        TrainingGroup active = group("Active", "Beginner");
        TrainingGroup archived = group("Archived", "Beginner").archive();
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(active, archived));

        // Act
        List<TrainingGroupListEntry> without = useCase.execute(new ListTrainingGroupsQuery(Data.actor(admin), false));

        // Assert
        assertThat(without).extracting(TrainingGroupListEntry::group).containsExactly(active);
    }

    @Test
    void withArchivedIncludedActiveGroupsComeFirstThenArchivedEachInRepositoryOrder() {
        // Arrange
        TrainingGroup archivedA = group("A archived", "Beginner").archive();
        TrainingGroup activeB = group("B active", "Beginner");
        TrainingGroup archivedC = group("C archived", "Beginner").archive();
        TrainingGroup activeD = group("D active", "Beginner");
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(archivedA, activeB, archivedC, activeD));

        // Act
        List<TrainingGroupListEntry> entries = useCase.execute(new ListTrainingGroupsQuery(Data.actor(admin), true));

        // Assert
        assertThat(entries).extracting(TrainingGroupListEntry::group).containsExactly(activeB, activeD, archivedA, archivedC);
    }

    @Test
    void coachesAreNamedInOneBatchAndVenuesInOneQuery() {
        // Arrange
        TrainingGroup first = group("First", "Beginner");
        TrainingGroup second = group("Second", "Beginner");
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(first, second));

        // Act
        useCase.execute(new ListTrainingGroupsQuery(Data.actor(admin), false));

        // Assert
        verify(members, times(1)).findByIds(eq(association.id()), any());
        verify(venues, times(1)).findAllByAssociation(association.id());
    }

    @Test
    void aVenueOrCoachThatCannotBeFoundLeavesTheNameOutOfTheEntry() {
        // Arrange
        Venue elsewhere = Data.venue(association, "Not listed");
        TrainingGroup orphan = Data.groupAt(association, Data.coach(association), elsewhere, "Beginner");
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(orphan));

        // Act
        List<TrainingGroupListEntry> entries = useCase.execute(new ListTrainingGroupsQuery(Data.actor(admin), false));

        // Assert
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.venueName()).isEmpty();
            assertThat(entry.coachName()).isEmpty();
        });
    }

    @Test
    void noGroupsGiveAnEmptyListAndReadNothingElse() {
        // Arrange
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of());

        // Act
        List<TrainingGroupListEntry> entries = useCase.execute(new ListTrainingGroupsQuery(Data.actor(admin), true));

        // Assert
        assertThat(entries).isEmpty();
        verify(members, never()).findByIds(any(), any());
        verify(venues, never()).findAllByAssociation(any());
    }

    @Test
    void aCoachIsNotAllowedAndNothingIsRead() {
        // Arrange
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ListTrainingGroupsQuery(Data.actor(coach), false));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(groups, venues);
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListTrainingGroupsQuery(Data.actor(stranger), false));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(groups, venues);
    }
}
