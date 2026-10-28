package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListVenuesQuery;
import com.regivolley.api.application.result.VenueListEntry;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-02: the administrator's venue list, with whether an active group runs at each. */
@ExtendWith(MockitoExtension.class)
class ListVenuesServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private VenueRepository venues;
    @Mock
    private TrainingGroupRepository groups;

    private Association association;
    private Member admin;
    private ListVenuesUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        useCase = new ListVenuesService(members, venues, groups);
    }

    @Test
    void listsTheVenuesInTheOrderTheRepositoryGivesThemAndMarksOnlyThoseWithAnActiveGroup() {
        // Arrange
        Venue busy = Data.venue(association, "Busy");
        Venue idle = Data.venue(association, "Idle");
        Venue formerlyBusy = Data.venue(association, "Formerly busy");
        TrainingGroup active = Data.groupAt(association, admin, busy, "Beginner");
        TrainingGroup archived = Data.groupAt(association, admin, formerlyBusy, "Beginner").archive();
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(venues.findAllByAssociation(association.id())).thenReturn(List.of(busy, formerlyBusy, idle));
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(active, archived));

        // Act
        List<VenueListEntry> entries = useCase.execute(new ListVenuesQuery(Data.actor(admin)));

        // Assert
        assertThat(entries).extracting(VenueListEntry::venue).containsExactly(busy, formerlyBusy, idle);
        assertThat(entries).extracting(VenueListEntry::inUseByActiveGroup).containsExactly(true, false, false);
        verify(groups, times(1)).findAllByAssociation(association.id());
    }

    @Test
    void noVenuesGiveAnEmptyListAndNoGroupQuery() {
        // Arrange
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(venues.findAllByAssociation(association.id())).thenReturn(List.of());

        // Act
        List<VenueListEntry> entries = useCase.execute(new ListVenuesQuery(Data.actor(admin)));

        // Assert
        assertThat(entries).isEmpty();
        verify(groups, never()).findAllByAssociation(any());
    }

    @Test
    void aCoachIsNotAllowedAndNothingIsRead() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ListVenuesQuery(Data.actor(coach)));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(venues, groups);
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListVenuesQuery(Data.actor(stranger)));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(venues, groups);
    }
}
