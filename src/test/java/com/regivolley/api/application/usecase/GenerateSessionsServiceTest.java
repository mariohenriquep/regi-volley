package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.result.SessionGenerationReport;
import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateSessionsServiceTest {

    /** Four weeks from Monday 12 Oct 10:00 Lisbon: Wednesdays 14 Oct, 21 Oct, 28 Oct (after the clock change) and 4 Nov. */
    private static final Instant WINDOW_END = Instant.parse("2026-11-09T10:00:00Z");

    @Mock
    private AssociationRepository associations;
    @Mock
    private TrainingGroupRepository groups;
    @Mock
    private MemberRepository members;
    @Mock
    private SessionRepository sessions;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member coach;
    private TrainingGroup group;
    private GenerateSessionsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        group = Data.group(association, coach, "Beginner", "Intermediate");
        useCase = new GenerateSessionsService(associations, groups, members, sessions, transactions, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(List.of(group));
        lenient().when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        lenient().when(sessions.save(any(Session.class))).thenAnswer(returnsFirstArg());
    }

    private void existing(Session... stored) {
        lenient().when(sessions.findByTrainingGroupStartingBetween(association.id(), group.id(), Data.NOW, WINDOW_END))
                .thenReturn(List.of(stored));
    }

    private GenerateSessionsCommand command() {
        return new GenerateSessionsCommand(association.id());
    }

    @Test
    void createsTheSessionsOfTheWindowInheritingCapacityAndCoach() {
        // Arrange
        existing();

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isEqualTo(4);
        assertThat(report.skippedGroups()).isEmpty();
        ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
        verify(sessions, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(session -> {
            assertThat(session.associationId()).isEqualTo(association.id());
            assertThat(session.trainingGroupId()).isEqualTo(group.id());
            assertThat(session.coachId()).isEqualTo(coach.id());
            assertThat(session.capacity()).isEqualTo(group.defaultCapacity());
        });
        assertThat(saved.getAllValues()).extracting(Session::startsAt).containsExactly(
                Instant.parse("2026-10-14T19:00:00Z"), Instant.parse("2026-10-21T19:00:00Z"),
                Instant.parse("2026-10-28T20:00:00Z"), Instant.parse("2026-11-04T20:00:00Z"));
    }

    @Test
    void usesTheAssociationsOwnWindow() {
        // Arrange
        association = Association.reconstruct(association.id(), association.name(), association.shortName(), null,
                association.locality(), association.contactEmail(), association.bookingPolicy(),
                new SessionGenerationPolicy(1), NoShowPolicy.defaults(), association.levels(), association.entryLevelId(), 0L);
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
        when(sessions.findByTrainingGroupStartingBetween(association.id(), group.id(), Data.NOW,
                Instant.parse("2026-10-19T09:00:00Z"))).thenReturn(List.of());

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isEqualTo(1);
    }

    @Test
    void neverRecreatesASessionThatExistsEvenWhenItIsCancelled() {
        // Arrange
        Session cancelled = Data.session(group, Instant.parse("2026-10-14T19:00:00Z"), 12).cancel("Venue closed");
        Session booked = Data.session(group, Instant.parse("2026-10-21T19:00:00Z"), 12);
        existing(cancelled, booked);

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isEqualTo(2);
        ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
        verify(sessions, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Session::startsAt)
                .containsExactly(Instant.parse("2026-10-28T20:00:00Z"), Instant.parse("2026-11-04T20:00:00Z"));
    }

    @Test
    void runningAgainWhenEverythingExistsCreatesNothing() {
        // Arrange
        existing(Data.session(group, Instant.parse("2026-10-14T19:00:00Z"), 12),
                Data.session(group, Instant.parse("2026-10-21T19:00:00Z"), 12),
                Data.session(group, Instant.parse("2026-10-28T20:00:00Z"), 12),
                Data.session(group, Instant.parse("2026-11-04T20:00:00Z"), 12));

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isZero();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void skipsArchivedGroupsWithoutLoadingTheirSessions() {
        // Arrange
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(group.archive()));

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isZero();
        assertThat(report.skippedGroups()).isEmpty();
        verify(sessions, never()).findByTrainingGroupStartingBetween(any(), any(), any(), any());
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void skipsAGroupWhoseCoachDoesNotHoldTheCoachRole() {
        // Arrange
        Member plainMember = Data.member(association);
        TrainingGroup badCoach = group.changeCoach(plainMember.id());
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(badCoach));
        when(members.findById(association.id(), plainMember.id())).thenReturn(Optional.of(plainMember));

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isZero();
        assertThat(report.skippedGroups()).containsExactly(group.id());
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void skipsAGroupWhoseCoachIsNotAMemberOfTheAssociation() {
        // Arrange
        Member elsewhere = Data.coach(Data.association());
        TrainingGroup foreignCoach = group.changeCoach(elsewhere.id());
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(foreignCoach));
        when(members.findById(association.id(), elsewhere.id())).thenReturn(Optional.empty());

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.skippedGroups()).containsExactly(group.id());
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void skipsAGroupAcceptingALevelOfAnotherAssociation() {
        // Arrange
        TrainingGroup foreignLevel = group.changeAcceptedLevels(Set.of(LevelId.generate()));
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(foreignLevel));

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.skippedGroups()).containsExactly(group.id());
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void aLostRaceIsRetriedAgainstTheStateTheWinnerLeft() {
        // Arrange
        Session winner = Data.session(group, Instant.parse("2026-10-14T19:00:00Z"), 12);
        when(sessions.findByTrainingGroupStartingBetween(association.id(), group.id(), Data.NOW, WINDOW_END))
                .thenReturn(List.of())
                .thenReturn(List.of(winner, Data.session(group, Instant.parse("2026-10-21T19:00:00Z"), 12),
                        Data.session(group, Instant.parse("2026-10-28T20:00:00Z"), 12),
                        Data.session(group, Instant.parse("2026-11-04T20:00:00Z"), 12)));
        when(sessions.save(any(Session.class))).thenThrow(new SessionModifiedConcurrentlyException(winner.id()));

        // Act
        SessionGenerationReport report = useCase.execute(command());

        // Assert
        assertThat(report.sessionsCreated()).isZero();
        assertThat(transactions.opened()).isEqualTo(2);
    }

    @Test
    void anUnknownAssociationIsNotFound() {
        // Arrange
        when(associations.findById(association.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(command());

        // Act
        AssociationNotFoundException ex = assertThrows(AssociationNotFoundException.class, act);

        // Assert
        assertThat(ex.associationId()).isEqualTo(association.id());
    }
}
