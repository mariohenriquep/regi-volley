package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetMyRolesQuery;
import com.regivolley.api.application.result.MyRoles;
import com.regivolley.api.domain.exception.MemberNotFoundException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** The roles the caller holds now, read from their own Member on every call (architecture.md section 11). */
@ExtendWith(MockitoExtension.class)
class GetMyRolesServiceTest {

    @Mock
    private MemberRepository members;

    private Association association;
    private GetMyRolesUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        useCase = new GetMyRolesService(members);
    }

    private MyRoles rolesOf(Member who) {
        when(members.findById(association.id(), who.id())).thenReturn(Optional.of(who));
        return useCase.execute(new GetMyRolesQuery(Data.actor(who)));
    }

    @Test
    void aPlainMemberHoldsOnlyMember() {
        // Arrange
        Member plain = Data.member(association);

        // Act
        MyRoles roles = rolesOf(plain);

        // Assert
        assertThat(roles.roles()).containsExactly(MemberRole.MEMBER);
    }

    @Test
    void severalRolesAreListedInTheOrderOfTheEnumNoMatterHowTheyWereGranted() {
        // Arrange
        Member everything = Data.member(association, MemberRole.ADMIN, MemberRole.MEMBER, MemberRole.COACH);

        // Act
        MyRoles roles = rolesOf(everything);

        // Assert
        assertThat(roles.roles()).containsExactly(MemberRole.MEMBER, MemberRole.COACH, MemberRole.ADMIN);
    }

    @Test
    void aRevokedRoleIsGoneOnTheNextCall() {
        // Arrange
        Member coach = Data.member(association, MemberRole.MEMBER, MemberRole.COACH);
        MyRoles before = rolesOf(coach);
        Member revoked = coach.revokeRole(MemberRole.COACH);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(revoked));

        // Act
        MyRoles after = useCase.execute(new GetMyRolesQuery(Data.actor(coach)));

        // Assert
        assertThat(before.roles()).containsExactly(MemberRole.MEMBER, MemberRole.COACH);
        assertThat(after.roles()).containsExactly(MemberRole.MEMBER);
    }

    @Test
    void aDeactivatedMemberHoldsNoPowerWhateverRolesTheyWereLeftWith() {
        // Arrange
        Member admin = Data.member(association, MemberRole.MEMBER, MemberRole.ADMIN).deactivate();

        // Act
        MyRoles roles = rolesOf(admin);

        // Assert
        assertThat(roles.roles()).containsExactly(MemberRole.MEMBER);
    }

    @Test
    void anUnknownMemberIsNotFoundAndTheLookupIsInTheActorsAssociation() {
        // Arrange
        Member stranger = Data.member(association);
        when(members.findById(association.id(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new GetMyRolesQuery(Data.actor(stranger)));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(members).findById(association.id(), stranger.id());
        verifyNoMoreInteractions(members);
    }
}
