package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeMemberLevelCommand;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeMemberLevelServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member coach;
    private Member target;
    private ChangeMemberLevelUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        target = Data.member(association);
        useCase = new ChangeMemberLevelService(members, associations, transactions, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(members.save(any(Member.class))).thenAnswer(returnsFirstArg());
        for (Member person : new Member[]{admin, coach, target}) {
            lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
        }
    }

    private ChangeMemberLevelCommand moveTo(Member actor, String levelName) {
        return new ChangeMemberLevelCommand(Data.actor(actor), target.id(), Data.level(association, levelName));
    }

    @Test
    void anAdministratorMovesAMemberAndTheChangeRecordsWhoDidIt() {
        // Arrange
        ChangeMemberLevelCommand command = moveTo(admin, "Advanced");

        // Act
        Member changed = useCase.execute(command);

        // Assert
        assertThat(changed.levelId()).isEqualTo(Data.level(association, "Advanced"));
        assertThat(changed.levelChanges()).singleElement().satisfies(change -> {
            assertThat(change.changedBy()).isEqualTo(admin.id());
            assertThat(change.changedAt()).isEqualTo(Data.NOW);
        });
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(members).save(saved.capture());
        assertThat(saved.getValue().levelId()).isEqualTo(changed.levelId());
    }

    @Test
    void aCoachMayMoveAMemberToo() {
        // Arrange
        ChangeMemberLevelCommand command = moveTo(coach, "Intermediate");

        // Act
        Member changed = useCase.execute(command);

        // Assert
        assertThat(changed.levelChanges().get(0).changedBy()).isEqualTo(coach.id());
    }

    @Test
    void movingToTheLevelTheyAlreadyHaveStoresNothing() {
        // Arrange
        ChangeMemberLevelCommand command = moveTo(admin, "Beginner");

        // Act
        Member same = useCase.execute(command);

        // Assert
        assertThat(same.levelChanges()).isEmpty();
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aPlainMemberIsNotAllowed() {
        // Arrange
        Member plain = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> useCase.execute(moveTo(plain, "Advanced"));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aDeactivatedCoachHoldsNoPowers() {
        // Arrange
        Member gone = Data.coach(association).deactivate();
        when(members.findById(association.id(), gone.id())).thenReturn(Optional.of(gone));
        Executable act = () -> useCase.execute(moveTo(gone, "Advanced"));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aLevelOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> useCase.execute(new ChangeMemberLevelCommand(Data.actor(admin), target.id(), LevelId.generate()));

        // Act
        assertThrows(LevelNotFoundException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aMemberOfAnotherAssociationIsNotFound() {
        // Arrange
        Member foreign = Data.member(Data.association());
        Executable act = () -> useCase.execute(new ChangeMemberLevelCommand(Data.actor(admin), foreign.id(),
                Data.level(association, "Advanced")));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void anActorOfAnotherAssociationIsNotFound() {
        // Arrange
        Association other = Data.association();
        Member foreignAdmin = Data.admin(other);
        Executable act = () -> useCase.execute(new ChangeMemberLevelCommand(Data.actor(foreignAdmin), target.id(),
                Data.level(association, "Advanced")));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void retriesInANewTransactionWhenTheMemberWasChangedMeanwhile() {
        // Arrange
        when(members.save(any(Member.class)))
                .thenThrow(new MemberModifiedConcurrentlyException(target.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        Member changed = useCase.execute(moveTo(admin, "Advanced"));

        // Assert
        assertThat(changed.levelId()).isEqualTo(Data.level(association, "Advanced"));
        assertThat(transactions.opened()).isEqualTo(2);
    }

    @Test
    void theRoleOfTheActorIsReadFromTheirMember() {
        // Arrange
        Member coachAndMember = Data.member(association, MemberRole.COACH, MemberRole.MEMBER);
        when(members.findById(association.id(), coachAndMember.id())).thenReturn(Optional.of(coachAndMember));

        // Act
        Member changed = useCase.execute(moveTo(coachAndMember, "Advanced"));

        // Assert
        assertThat(changed.levelChanges()).hasSize(1);
    }
}
