package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberRole;
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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantRoleServiceTest {

    @Mock
    private MemberRepository members;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member target;
    private GrantRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        target = Data.member(association);
        useCase = new GrantRoleService(members, transactions);
        lenient().when(members.save(any(Member.class))).thenAnswer(returnsFirstArg());
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
    }

    @Test
    void anAdministratorGrantsARole() {
        // Arrange
        GrantRoleCommand command = new GrantRoleCommand(Data.actor(admin), target.id(), MemberRole.COACH);

        // Act
        Member granted = useCase.execute(command);

        // Assert
        assertThat(granted.roles()).containsExactlyInAnyOrder(MemberRole.MEMBER, MemberRole.COACH);
        verify(members).save(granted);
    }

    @Test
    void aCoachCannotGrantRoles() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new GrantRoleCommand(Data.actor(coach), target.id(), MemberRole.ADMIN));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aDeactivatedAdministratorCannotGrantRoles() {
        // Arrange
        Member gone = admin.deactivate();
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(gone));
        Executable act = () -> useCase.execute(new GrantRoleCommand(Data.actor(admin), target.id(), MemberRole.COACH));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aMemberOfAnotherAssociationIsNotFound() {
        // Arrange
        Member foreign = Data.member(Data.association());
        Executable act = () -> useCase.execute(new GrantRoleCommand(Data.actor(admin), foreign.id(), MemberRole.COACH));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void retriesInANewTransactionOnAConflict() {
        // Arrange
        when(members.save(any(Member.class)))
                .thenThrow(new MemberModifiedConcurrentlyException(target.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        Member granted = useCase.execute(new GrantRoleCommand(Data.actor(admin), target.id(), MemberRole.COACH));

        // Assert
        assertThat(granted.hasRole(MemberRole.COACH)).isTrue();
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
