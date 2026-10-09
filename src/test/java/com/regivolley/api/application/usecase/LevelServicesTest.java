package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AddLevelCommand;
import com.regivolley.api.application.command.ChangeEntryLevelCommand;
import com.regivolley.api.application.command.RenameLevelCommand;
import com.regivolley.api.application.command.ReorderLevelsCommand;
import com.regivolley.api.domain.exception.AssociationModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.DuplicateLevelNameException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-03: the four level operations share their rules (administrator only, tenant from the actor, retry), so one class covers them. */
@ExtendWith(MockitoExtension.class)
class LevelServicesTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private MemberRepository members;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member coach;
    private AddLevelUseCase add;
    private RenameLevelUseCase rename;
    private ReorderLevelsUseCase reorder;
    private ChangeEntryLevelUseCase changeEntry;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        add = new AddLevelService(associations, members, transactions);
        rename = new RenameLevelService(associations, members, transactions);
        reorder = new ReorderLevelsService(associations, members, transactions);
        changeEntry = new ChangeEntryLevelService(associations, members, transactions);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(associations.save(any(Association.class))).thenAnswer(returnsFirstArg());
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
    }

    private LevelId id(String name) {
        return Data.level(association, name);
    }

    @Test
    void addsALevelAsTheMostAdvancedOne() {
        // Arrange
        AddLevelCommand command = new AddLevelCommand(Data.actor(admin), "Pro");

        // Act
        Association changed = add.execute(command);

        // Assert
        assertThat(changed.levels()).extracting(Level::name).containsExactly("Beginner", "Intermediate", "Advanced", "Pro");
        verify(associations).save(changed);
    }

    @Test
    void aDuplicateLevelNameIsRejectedAndNothingIsStored() {
        // Arrange
        Executable act = () -> add.execute(new AddLevelCommand(Data.actor(admin), " beginner "));

        // Act
        assertThrows(DuplicateLevelNameException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void renamesALevel() {
        // Arrange
        RenameLevelCommand command = new RenameLevelCommand(Data.actor(admin), id("Intermediate"), "Improver");

        // Act
        Association changed = rename.execute(command);

        // Assert
        assertThat(changed.level(id("Intermediate")).name()).isEqualTo("Improver");
    }

    @Test
    void renamingALevelOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> rename.execute(new RenameLevelCommand(Data.actor(admin), LevelId.generate(), "X"));

        // Act
        assertThrows(LevelNotFoundException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void reordersTheLevels() {
        // Arrange
        ReorderLevelsCommand command = new ReorderLevelsCommand(Data.actor(admin),
                List.of(id("Advanced"), id("Beginner"), id("Intermediate")));

        // Act
        Association changed = reorder.execute(command);

        // Assert
        assertThat(changed.levels()).extracting(Level::name).containsExactly("Advanced", "Beginner", "Intermediate");
        assertThat(changed.entryLevelId()).isEqualTo(association.entryLevelId());
    }

    @Test
    void reorderingMustListEveryLevelExactlyOnce() {
        // Arrange
        Executable act = () -> reorder.execute(new ReorderLevelsCommand(Data.actor(admin),
                List.of(id("Advanced"), id("Beginner"))));

        // Act
        assertThrows(InvalidFieldException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void changesTheEntryLevel() {
        // Arrange
        ChangeEntryLevelCommand command = new ChangeEntryLevelCommand(Data.actor(admin), id("Intermediate"));

        // Act
        Association changed = changeEntry.execute(command);

        // Assert
        assertThat(changed.entryLevelId()).isEqualTo(id("Intermediate"));
    }

    @Test
    void theEntryLevelMustBeOneOfTheAssociationsLevels() {
        // Arrange
        Executable act = () -> changeEntry.execute(new ChangeEntryLevelCommand(Data.actor(admin), LevelId.generate()));

        // Act
        assertThrows(LevelNotFoundException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void onlyAnAdministratorMayTouchTheLevels() {
        // Arrange
        Executable addAttempt = () -> add.execute(new AddLevelCommand(Data.actor(coach), "Pro"));
        Executable renameAttempt = () -> rename.execute(new RenameLevelCommand(Data.actor(coach), id("Beginner"), "X"));
        Executable reorderAttempt = () -> reorder.execute(new ReorderLevelsCommand(Data.actor(coach),
                List.of(id("Advanced"), id("Beginner"), id("Intermediate"))));
        Executable entryAttempt = () -> changeEntry.execute(new ChangeEntryLevelCommand(Data.actor(coach), id("Advanced")));

        // Act
        assertThrows(NotAllowedException.class, addAttempt);
        assertThrows(NotAllowedException.class, renameAttempt);
        assertThrows(NotAllowedException.class, reorderAttempt);
        assertThrows(NotAllowedException.class, entryAttempt);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void anAdministratorOfAnotherAssociationIsNotFoundInThisOne() {
        // Arrange
        Member foreignAdmin = Data.admin(Data.association());
        Executable act = () -> add.execute(new AddLevelCommand(Data.actor(foreignAdmin), "Pro"));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void everyOperationRetriesInANewTransactionOnAConflict() {
        // Arrange
        var conflict = new AssociationModifiedConcurrentlyException(association.id());
        when(associations.save(any(Association.class)))
                .thenThrow(conflict).thenAnswer(returnsFirstArg())
                .thenThrow(conflict).thenAnswer(returnsFirstArg())
                .thenThrow(conflict).thenAnswer(returnsFirstArg())
                .thenThrow(conflict).thenAnswer(returnsFirstArg());

        // Act
        Association afterAdd = add.execute(new AddLevelCommand(Data.actor(admin), "Pro"));
        Association afterRename = rename.execute(new RenameLevelCommand(Data.actor(admin), id("Beginner"), "Novice"));
        Association afterReorder = reorder.execute(new ReorderLevelsCommand(Data.actor(admin),
                List.of(id("Advanced"), id("Beginner"), id("Intermediate"))));
        Association afterEntry = changeEntry.execute(new ChangeEntryLevelCommand(Data.actor(admin), id("Advanced")));

        // Assert
        assertThat(afterAdd.levels()).hasSize(4);
        assertThat(afterRename.level(id("Beginner")).name()).isEqualTo("Novice");
        assertThat(afterReorder.levels().get(0).name()).isEqualTo("Advanced");
        assertThat(afterEntry.entryLevelId()).isEqualTo(id("Advanced"));
        assertThat(transactions.opened()).isEqualTo(8);
    }
}
