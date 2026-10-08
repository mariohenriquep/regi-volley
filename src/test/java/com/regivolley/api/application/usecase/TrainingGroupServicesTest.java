package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ArchiveTrainingGroupCommand;
import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.application.command.EditTrainingGroupCommand;
import com.regivolley.api.domain.exception.AtLeastOneAcceptedLevelRequiredException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.InvalidCoachException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.TrainingGroupArchivedException;
import com.regivolley.api.domain.exception.TrainingGroupModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.TrainingGroupNotFoundException;
import com.regivolley.api.domain.exception.VenueNotFoundException;
import com.regivolley.api.domain.factory.VenueFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-09: create, edit and archive a training group. */
@ExtendWith(MockitoExtension.class)
class TrainingGroupServicesTest {

    private static final WeeklySchedule FRIDAYS = WeeklySchedule.of(
            new WeeklySlot(DayOfWeek.FRIDAY, LocalTime.of(21, 0), Duration.ofMinutes(120)));

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private VenueRepository venues;
    @Mock
    private TrainingGroupRepository groups;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member coach;
    private Venue venue;
    private TrainingGroup group;
    private CreateTrainingGroupUseCase create;
    private EditTrainingGroupUseCase edit;
    private ArchiveTrainingGroupUseCase archive;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        venue = VenueFactory.create(association.id(), "Pavilhao", "Rua A", 2);
        group = Data.group(association, coach, "Beginner");
        create = new CreateTrainingGroupService(members, associations, venues, groups, transactions);
        edit = new EditTrainingGroupService(members, associations, groups, transactions);
        archive = new ArchiveTrainingGroupService(members, groups, transactions);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        lenient().when(venues.findByIdForUpdate(association.id(), venue.id())).thenReturn(Optional.of(venue));
        lenient().when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
        lenient().when(groups.save(any(TrainingGroup.class))).thenAnswer(returnsFirstArg());
    }

    private CreateTrainingGroupCommand creation(Member actor, Set<LevelId> levels, VenueId venueId, int capacity, MemberId coachId) {
        return new CreateTrainingGroupCommand(Data.actor(actor), "Thursday group", levels, venueId, FRIDAYS, capacity, coachId);
    }

    private Set<LevelId> levels(String... names) {
        return java.util.stream.Stream.of(names).map(name -> Data.level(association, name)).collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------------ create

    @Test
    void createsAnActiveGroupWithEverythingTheAdministratorGave() {
        // Arrange
        CreateTrainingGroupCommand command = creation(admin, levels("Beginner", "Intermediate"), venue.id(), 14, coach.id());

        // Act
        TrainingGroup created = create.execute(command);

        // Assert
        assertThat(created.associationId()).isEqualTo(association.id());
        assertThat(created.name()).isEqualTo("Thursday group");
        assertThat(created.acceptedLevels()).isEqualTo(levels("Beginner", "Intermediate"));
        assertThat(created.venueId()).isEqualTo(venue.id());
        assertThat(created.schedule()).isEqualTo(FRIDAYS);
        assertThat(created.defaultCapacity()).isEqualTo(14);
        assertThat(created.coachId()).isEqualTo(coach.id());
        assertThat(created.status()).isEqualTo(TrainingGroupStatus.ACTIVE);
        verify(groups).save(created);
        verify(venues).findByIdForUpdate(association.id(), venue.id());
    }

    @Test
    void onlyAnAdministratorMayManageGroups() {
        // Arrange
        Executable createAttempt = () -> create.execute(creation(coach, levels("Beginner"), venue.id(), 12, coach.id()));
        Executable editAttempt = () -> edit.execute(new EditTrainingGroupCommand(Data.actor(coach), group.id(), "X",
                levels("Beginner"), FRIDAYS, 12, coach.id()));
        Executable archiveAttempt = () -> archive.execute(new ArchiveTrainingGroupCommand(Data.actor(coach), group.id()));

        // Act
        assertThrows(NotAllowedException.class, createAttempt);
        assertThrows(NotAllowedException.class, editAttempt);
        assertThrows(NotAllowedException.class, archiveAttempt);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void theCoachMustHoldTheCoachRole() {
        // Arrange
        Member plain = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> create.execute(creation(admin, levels("Beginner"), venue.id(), 12, plain.id()));

        // Act
        InvalidCoachException ex = assertThrows(InvalidCoachException.class, act);

        // Assert
        assertThat(ex.coachId()).isEqualTo(plain.id());
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void theCoachMustBeActive() {
        // Arrange
        Member gone = Data.coach(association).deactivate();
        when(members.findById(association.id(), gone.id())).thenReturn(Optional.of(gone));
        Executable act = () -> create.execute(creation(admin, levels("Beginner"), venue.id(), 12, gone.id()));

        // Act
        assertThrows(InvalidCoachException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void aCoachOfAnotherAssociationIsAnInvalidCoach() {
        // Arrange
        Member foreignCoach = Data.coach(Data.association());
        Executable act = () -> create.execute(creation(admin, levels("Beginner"), venue.id(), 12, foreignCoach.id()));

        // Act
        assertThrows(InvalidCoachException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void anAcceptedLevelOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> create.execute(creation(admin, Set.of(LevelId.generate()), venue.id(), 12, coach.id()));

        // Act
        assertThrows(LevelNotFoundException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void aVenueOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> create.execute(creation(admin, levels("Beginner"), VenueId.generate(), 12, coach.id()));

        // Act
        assertThrows(VenueNotFoundException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void theDomainStillRejectsNoLevelsAndABadCapacity() {
        // Arrange
        Executable noLevels = () -> create.execute(creation(admin, Set.of(), venue.id(), 12, coach.id()));
        Executable noCapacity = () -> create.execute(creation(admin, levels("Beginner"), venue.id(), 0, coach.id()));

        // Act
        assertThrows(AtLeastOneAcceptedLevelRequiredException.class, noLevels);
        assertThrows(InvalidCapacityException.class, noCapacity);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    // -------------------------------------------------------------------- edit

    @Test
    void editsNameLevelsCapacityCoachAndScheduleAndKeepsTheVenue() {
        // Arrange
        Member newCoach = Data.coach(association);
        when(members.findById(association.id(), newCoach.id())).thenReturn(Optional.of(newCoach));
        EditTrainingGroupCommand command = new EditTrainingGroupCommand(Data.actor(admin), group.id(), "Renamed",
                levels("Advanced"), FRIDAYS, 16, newCoach.id());

        // Act
        TrainingGroup edited = edit.execute(command);

        // Assert
        assertThat(edited.id()).isEqualTo(group.id());
        assertThat(edited.name()).isEqualTo("Renamed");
        assertThat(edited.acceptedLevels()).isEqualTo(levels("Advanced"));
        assertThat(edited.defaultCapacity()).isEqualTo(16);
        assertThat(edited.coachId()).isEqualTo(newCoach.id());
        assertThat(edited.schedule()).isEqualTo(FRIDAYS);
        assertThat(edited.venueId()).isEqualTo(group.venueId());
        verify(groups).save(edited);
    }

    @Test
    void editingValidatesTheCoachAndTheLevelsLikeCreating() {
        // Arrange
        Member plain = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable badCoach = () -> edit.execute(new EditTrainingGroupCommand(Data.actor(admin), group.id(), "X",
                levels("Beginner"), FRIDAYS, 12, plain.id()));
        Executable badLevel = () -> edit.execute(new EditTrainingGroupCommand(Data.actor(admin), group.id(), "X",
                Set.of(LevelId.generate()), FRIDAYS, 12, coach.id()));

        // Act
        assertThrows(InvalidCoachException.class, badCoach);
        assertThrows(LevelNotFoundException.class, badLevel);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void editingAGroupOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> edit.execute(new EditTrainingGroupCommand(Data.actor(admin), TrainingGroupId.generate(), "X",
                levels("Beginner"), FRIDAYS, 12, coach.id()));

        // Act
        assertThrows(TrainingGroupNotFoundException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void anArchivedGroupCannotBeEdited() {
        // Arrange
        when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group.archive()));
        Executable act = () -> edit.execute(new EditTrainingGroupCommand(Data.actor(admin), group.id(), "X",
                levels("Beginner"), FRIDAYS, 12, coach.id()));

        // Act
        assertThrows(TrainingGroupArchivedException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void editRetriesInANewTransactionOnAConflict() {
        // Arrange
        when(groups.save(any(TrainingGroup.class)))
                .thenThrow(new TrainingGroupModifiedConcurrentlyException(group.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        TrainingGroup edited = edit.execute(new EditTrainingGroupCommand(Data.actor(admin), group.id(), "X",
                levels("Beginner"), FRIDAYS, 12, coach.id()));

        // Assert
        assertThat(edited.name()).isEqualTo("X");
        assertThat(transactions.opened()).isEqualTo(2);
    }

    // ----------------------------------------------------------------- archive

    @Test
    void archivesAGroup() {
        // Arrange
        ArchiveTrainingGroupCommand command = new ArchiveTrainingGroupCommand(Data.actor(admin), group.id());

        // Act
        TrainingGroup archived = archive.execute(command);

        // Assert
        assertThat(archived.status()).isEqualTo(TrainingGroupStatus.ARCHIVED);
        verify(groups).save(archived);
    }

    @Test
    void archivingTwiceIsRejected() {
        // Arrange
        when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group.archive()));
        Executable act = () -> archive.execute(new ArchiveTrainingGroupCommand(Data.actor(admin), group.id()));

        // Act
        assertThrows(TrainingGroupArchivedException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void archivingAGroupOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> archive.execute(new ArchiveTrainingGroupCommand(Data.actor(admin), TrainingGroupId.generate()));

        // Act
        assertThrows(TrainingGroupNotFoundException.class, act);

        // Assert
        verify(groups, never()).save(any(TrainingGroup.class));
    }

    @Test
    void archiveRetriesInANewTransactionOnAConflict() {
        // Arrange
        when(groups.save(any(TrainingGroup.class)))
                .thenThrow(new TrainingGroupModifiedConcurrentlyException(group.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        TrainingGroup archived = archive.execute(new ArchiveTrainingGroupCommand(Data.actor(admin), group.id()));

        // Assert
        assertThat(archived.status()).isEqualTo(TrainingGroupStatus.ARCHIVED);
        assertThat(transactions.opened()).isEqualTo(2);
    }

    @Test
    void anArchivedGroupIsRefusedBeforeAnythingElseIsChecked() {
        // Arrange
        when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group.archive()));
        Member plain = Data.member(association);
        Executable act = () -> edit.execute(new EditTrainingGroupCommand(Data.actor(admin), group.id(), "X",
                Set.of(LevelId.generate()), FRIDAYS, 12, plain.id()));

        // Act
        TrainingGroupArchivedException ex = assertThrows(TrainingGroupArchivedException.class, act);

        // Assert
        assertThat(ex.getMessage()).isNotBlank();
        verify(groups, never()).save(any(TrainingGroup.class));
    }
}
