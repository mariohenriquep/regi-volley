package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.application.command.DeleteVenueCommand;
import com.regivolley.api.application.command.EditVenueCommand;
import com.regivolley.api.application.result.VenueDeleted;
import com.regivolley.api.domain.exception.InvalidCourtCountException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.VenueInUseException;
import com.regivolley.api.domain.exception.VenueModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.VenueNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-02: create, edit and delete a venue; one class because the three share their rules (administrator only, tenant from the actor). */
@ExtendWith(MockitoExtension.class)
class VenueServicesTest {

    @Mock
    private MemberRepository members;
    @Mock
    private VenueRepository venues;
    @Mock
    private TrainingGroupRepository groups;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member coach;
    private Venue venue;
    private CreateVenueUseCase create;
    private EditVenueUseCase edit;
    private DeleteVenueUseCase delete;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        venue = Venue.create(association.id(), "Pavilhao Central", "Rua A 1", 2);
        create = new CreateVenueService(members, venues, transactions);
        edit = new EditVenueService(members, venues, transactions);
        delete = new DeleteVenueService(members, venues, groups, transactions);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        lenient().when(venues.save(any(Venue.class))).thenAnswer(returnsFirstArg());
        lenient().when(venues.findById(association.id(), venue.id())).thenReturn(Optional.of(venue));
        lenient().when(venues.findByIdForUpdate(association.id(), venue.id())).thenReturn(Optional.of(venue));
    }

    @Test
    void anAdministratorCreatesAVenueInTheirOwnAssociation() {
        // Arrange
        CreateVenueCommand command = new CreateVenueCommand(Data.actor(admin), "Pavilhao Norte", "Rua B 2", 3);

        // Act
        Venue created = create.execute(command);

        // Assert
        assertThat(created.associationId()).isEqualTo(association.id());
        assertThat(created.name()).isEqualTo("Pavilhao Norte");
        assertThat(created.courts()).isEqualTo(3);
        verify(venues).save(created);
        assertThat(transactions.opened()).isEqualTo(1);
    }

    @Test
    void aVenueNeedsAtLeastOneCourt() {
        // Arrange
        Executable act = () -> create.execute(new CreateVenueCommand(Data.actor(admin), "Pavilhao", "Rua", 0));

        // Act
        assertThrows(InvalidCourtCountException.class, act);

        // Assert
        verify(venues, never()).save(any(Venue.class));
    }

    @Test
    void onlyAnAdministratorMayCreateEditOrDelete() {
        // Arrange
        Executable createAttempt = () -> create.execute(new CreateVenueCommand(Data.actor(coach), "P", "R", 1));
        Executable editAttempt = () -> edit.execute(new EditVenueCommand(Data.actor(coach), venue.id(), "P", "R", 1));
        Executable deleteAttempt = () -> delete.execute(new DeleteVenueCommand(Data.actor(coach), venue.id()));

        // Act
        assertThrows(NotAllowedException.class, createAttempt);
        assertThrows(NotAllowedException.class, editAttempt);
        assertThrows(NotAllowedException.class, deleteAttempt);

        // Assert
        verify(venues, never()).save(any(Venue.class));
        verify(venues, never()).delete(any(Venue.class));
    }

    @Test
    void anAdministratorOfAnotherAssociationIsNotFoundHere() {
        // Arrange
        Member foreignAdmin = Data.admin(Data.association());
        Executable act = () -> create.execute(new CreateVenueCommand(Data.actor(foreignAdmin), "P", "R", 1));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(venues, never()).save(any(Venue.class));
    }

    @Test
    void anAdministratorEditsAVenueAndItKeepsItsIdentity() {
        // Arrange
        EditVenueCommand command = new EditVenueCommand(Data.actor(admin), venue.id(), "Pavilhao Sul", "Rua C 3", 4);

        // Act
        Venue edited = edit.execute(command);

        // Assert
        assertThat(edited.id()).isEqualTo(venue.id());
        assertThat(edited.name()).isEqualTo("Pavilhao Sul");
        assertThat(edited.courts()).isEqualTo(4);
        verify(venues).save(edited);
    }

    @Test
    void editingAVenueOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> edit.execute(new EditVenueCommand(Data.actor(admin), VenueId.generate(), "P", "R", 1));

        // Act
        assertThrows(VenueNotFoundException.class, act);

        // Assert
        verify(venues, never()).save(any(Venue.class));
    }

    @Test
    void editRetriesInANewTransactionOnAConflict() {
        // Arrange
        when(venues.save(any(Venue.class)))
                .thenThrow(new VenueModifiedConcurrentlyException(venue.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        Venue edited = edit.execute(new EditVenueCommand(Data.actor(admin), venue.id(), "P", "R", 1));

        // Assert
        assertThat(edited.name()).isEqualTo("P");
        assertThat(transactions.opened()).isEqualTo(2);
    }

    @Test
    void deletesAVenueNoActiveGroupUsesAfterTakingItsLock() {
        // Arrange
        when(groups.existsActiveWithVenue(association.id(), venue.id())).thenReturn(false);

        // Act
        VenueDeleted deleted = delete.execute(new DeleteVenueCommand(Data.actor(admin), venue.id()));

        // Assert
        assertThat(deleted.venueId()).isEqualTo(venue.id());
        verify(venues).findByIdForUpdate(association.id(), venue.id());
        verify(venues).delete(venue);
    }

    @Test
    void aVenueUsedByAnActiveGroupCannotBeDeleted() {
        // Arrange
        when(groups.existsActiveWithVenue(association.id(), venue.id())).thenReturn(true);
        Executable act = () -> delete.execute(new DeleteVenueCommand(Data.actor(admin), venue.id()));

        // Act
        VenueInUseException ex = assertThrows(VenueInUseException.class, act);

        // Assert
        assertThat(ex.venueId()).isEqualTo(venue.id());
        verify(venues, never()).delete(any(Venue.class));
    }

    @Test
    void deletingAVenueOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> delete.execute(new DeleteVenueCommand(Data.actor(admin), VenueId.generate()));

        // Act
        assertThrows(VenueNotFoundException.class, act);

        // Assert
        verify(venues, never()).delete(any(Venue.class));
    }

    @Test
    void deleteRetriesAndThenSeesWhatTheWinnerLeftBehind() {
        // Arrange
        when(groups.existsActiveWithVenue(association.id(), venue.id())).thenReturn(false, true);
        doThrow(new VenueModifiedConcurrentlyException(venue.id())).when(venues).delete(venue);
        Executable act = () -> delete.execute(new DeleteVenueCommand(Data.actor(admin), venue.id()));

        // Act
        assertThrows(VenueInUseException.class, act);

        // Assert
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
