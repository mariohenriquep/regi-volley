package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListPendingJoinRequestsQuery;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.repository.JoinRequestRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-06: the administrator sees the requests waiting for a decision. */
@ExtendWith(MockitoExtension.class)
class ListPendingJoinRequestsServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private JoinRequestRepository joinRequests;

    private Association association;
    private ListPendingJoinRequestsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        useCase = new ListPendingJoinRequestsService(members, joinRequests);
    }

    @Test
    void anAdministratorGetsTheAssociationsPendingRequests() {
        // Arrange
        Member admin = Data.admin(association);
        JoinRequest pending = JoinRequestFactory.create(association.id(), ContactDetails.of("Rita Costa",
                EmailAddress.of("rita@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-01", Data.CLOCK), Data.CLOCK);
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(joinRequests.findPending(association.id())).thenReturn(List.of(pending));

        // Act
        List<JoinRequest> found = useCase.execute(new ListPendingJoinRequestsQuery(Data.actor(admin)));

        // Assert
        assertThat(found).containsExactly(pending);
    }

    @Test
    void aPlainMemberIsNotAllowedAndNothingIsRead() {
        // Arrange
        Member member = Data.member(association);
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
        Executable act = () -> useCase.execute(new ListPendingJoinRequestsQuery(Data.actor(member)));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(joinRequests);
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListPendingJoinRequestsQuery(Data.actor(stranger)));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(joinRequests);
    }
}
