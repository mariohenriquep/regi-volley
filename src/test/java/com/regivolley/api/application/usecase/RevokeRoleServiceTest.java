package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.LastRoleCannotBeRevokedException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberRole;
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

@ExtendWith(MockitoExtension.class)
class RevokeRoleServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private RevokeRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER);
        useCase = new RevokeRoleService(members, associations, transactions);
        lenient().when(members.save(any(Member.class))).thenAnswer(returnsFirstArg());
        lenient().when(associations.findByIdForUpdate(association.id())).thenReturn(Optional.of(association));
        knows(admin);
    }

    private void knows(Member... people) {
        for (Member person : people) {
            lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
        }
    }

    @Test
    void anAdministratorRevokesACoachRoleWithoutAnyAdminCheck() {
        // Arrange
        Member coach = Data.member(association, MemberRole.COACH, MemberRole.MEMBER);
        knows(coach);

        // Act
        Member revoked = useCase.execute(new RevokeRoleCommand(Data.actor(admin), coach.id(), MemberRole.COACH));

        // Assert
        assertThat(revoked.roles()).containsExactly(MemberRole.MEMBER);
        verify(members, never()).findActiveAdminIds(any());
    }

    @Test
    void anAdministratorCanRevokeAnotherAdminsRoleWhileOneStays() {
        // Arrange
        Member other = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER);
        knows(other);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(admin.id(), other.id()));

        // Act
        Member revoked = useCase.execute(new RevokeRoleCommand(Data.actor(admin), other.id(), MemberRole.ADMIN));

        // Assert
        assertThat(revoked.hasRole(MemberRole.ADMIN)).isFalse();
        verify(members).save(revoked);
    }

    @Test
    void theLastAdministratorCannotGiveUpTheRole() {
        // Arrange
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(admin.id()));
        Executable act = () -> useCase.execute(new RevokeRoleCommand(Data.actor(admin), admin.id(), MemberRole.ADMIN));

        // Act
        assertThrows(LastAdministratorException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void anotherAdminWhoIsDeactivatedDoesNotCountAsOneToKeep() {
        // Arrange - the repository only ever returns active admins: the one asked about is the only one
        Member other = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER);
        knows(other);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(other.id()));
        Executable act = () -> useCase.execute(new RevokeRoleCommand(Data.actor(admin), other.id(), MemberRole.ADMIN));

        // Act
        assertThrows(LastAdministratorException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void revokingAdminFromADeactivatedAdminIsNotGuarded() {
        // Arrange
        Member gone = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER).deactivate();
        knows(gone);

        // Act
        Member revoked = useCase.execute(new RevokeRoleCommand(Data.actor(admin), gone.id(), MemberRole.ADMIN));

        // Assert
        assertThat(revoked.hasRole(MemberRole.ADMIN)).isFalse();
        verify(members, never()).findActiveAdminIds(any());
    }

    @Test
    void aMembersOnlyRoleCannotBeRevoked() {
        // Arrange
        Member plain = Data.member(association);
        knows(plain);
        Executable act = () -> useCase.execute(new RevokeRoleCommand(Data.actor(admin), plain.id(), MemberRole.MEMBER));

        // Act
        assertThrows(LastRoleCannotBeRevokedException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        knows(coach);
        Executable act = () -> useCase.execute(new RevokeRoleCommand(Data.actor(coach), admin.id(), MemberRole.ADMIN));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aMemberOfAnotherAssociationIsNotFound() {
        // Arrange
        Member foreign = Data.admin(Data.association());
        Executable act = () -> useCase.execute(new RevokeRoleCommand(Data.actor(admin), foreign.id(), MemberRole.ADMIN));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void retriesAndStillAppliesTheGuardAgainstTheFreshAdmins() {
        // Arrange
        Member other = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER);
        knows(other);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(admin.id(), other.id()));
        when(members.save(any(Member.class)))
                .thenThrow(new MemberModifiedConcurrentlyException(other.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        Member revoked = useCase.execute(new RevokeRoleCommand(Data.actor(admin), other.id(), MemberRole.ADMIN));

        // Assert
        assertThat(revoked.hasRole(MemberRole.ADMIN)).isFalse();
        assertThat(transactions.opened()).isEqualTo(2);
        verify(members, org.mockito.Mockito.times(2)).findActiveAdminIds(association.id());
    }

    @Test
    void revokingAdminTakesTheAssociationLockBeforeLoadingAnyMember() {
        // Arrange
        Member other = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER);
        knows(other);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(admin.id(), other.id()));

        // Act
        useCase.execute(new RevokeRoleCommand(Data.actor(admin), other.id(), MemberRole.ADMIN));

        // Assert
        var order = org.mockito.Mockito.inOrder(associations, members);
        order.verify(associations).findByIdForUpdate(association.id());
        order.verify(members).findById(association.id(), admin.id());
        order.verify(members).findActiveAdminIds(association.id());
    }

    @Test
    void revokingAnotherRoleNeedsNoAssociationLock() {
        // Arrange
        Member coach = Data.member(association, MemberRole.COACH, MemberRole.MEMBER);
        knows(coach);

        // Act
        useCase.execute(new RevokeRoleCommand(Data.actor(admin), coach.id(), MemberRole.COACH));

        // Assert
        verify(associations, never()).findByIdForUpdate(any());
    }
}
