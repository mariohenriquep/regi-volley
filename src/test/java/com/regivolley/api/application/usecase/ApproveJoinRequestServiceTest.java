package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.port.AccountProvisioner;
import com.regivolley.api.application.result.JoinRequestApproval;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.JoinRequestNotFoundException;
import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproveJoinRequestServiceTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private MemberRepository members;
    @Mock
    private JoinRequestRepository joinRequests;
    @Mock
    private Notifier notifier;
    @Mock
    private AccountProvisioner provisioner;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private JoinRequest request;
    private ApproveJoinRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        request = JoinRequestFactory.create(association.id(), ContactDetails.of("Rita Costa", EmailAddress.of("rita@example.com"),
                PhoneNumber.of("912345678")), true, "2026-01", Data.CLOCK);
        useCase = new ApproveJoinRequestService(associations, members, joinRequests, transactions, notifier, provisioner, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(joinRequests.findById(association.id(), request.id())).thenReturn(Optional.of(request));
        lenient().when(joinRequests.save(any(JoinRequest.class))).thenAnswer(returnsFirstArg());
        lenient().when(members.save(any(Member.class))).thenAnswer(returnsFirstArg());
    }

    @Test
    void approvingCreatesTheMemberAtTheEntryLevelSavesBothAndNotifiesAfterTheCommit() {
        // Arrange
        ApproveJoinRequestCommand command = new ApproveJoinRequestCommand(Data.actor(admin), request.id());

        // Act
        JoinRequestApproval approval = useCase.execute(command);

        // Assert
        assertThat(approval.request().status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(approval.request().decidedBy()).hasValue(admin.id());
        assertThat(approval.member().levelId()).isEqualTo(association.entryLevelId());
        assertThat(approval.member().roles()).containsExactly(MemberRole.MEMBER);
        assertThat(approval.member().associationId()).isEqualTo(association.id());
        var order = inOrder(joinRequests, members, notifier);
        order.verify(joinRequests).save(approval.request());
        order.verify(members).save(approval.member());
        order.verify(notifier).memberApproved(association.id(), approval.member().id());
        assertThat(transactions.opened()).isEqualTo(1);
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ApproveJoinRequestCommand(Data.actor(coach), request.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(joinRequests, never()).save(any(JoinRequest.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aRequestOfAnotherAssociationIsNotFound() {
        // Arrange
        JoinRequest foreign = JoinRequestFactory.create(Data.association().id(), request.contact(), true, "2026-01", Data.CLOCK);
        Executable act = () -> useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), foreign.id()));

        // Act
        assertThrows(JoinRequestNotFoundException.class, act);

        // Assert
        verify(joinRequests, never()).save(any(JoinRequest.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aRequestAlreadyDecidedCannotBeApprovedAgain() {
        // Arrange
        JoinRequest rejected = request.reject(admin.id(), null, Data.CLOCK);
        when(joinRequests.findById(association.id(), request.id())).thenReturn(Optional.of(rejected));
        Executable act = () -> useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), request.id()));

        // Act
        assertThrows(InvalidJoinRequestStatusTransitionException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void ifTheMemberCannotBeStoredNoNotificationIsSent() {
        // Arrange
        when(members.save(any(Member.class))).thenThrow(new MemberEmailAlreadyUsedException());
        Executable act = () -> useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), request.id()));

        // Act
        assertThrows(MemberEmailAlreadyUsedException.class, act);

        // Assert
        verifyNoInteractions(notifier);
    }

    @Test
    void retriesInANewTransactionAndNotifiesOnlyOnce() {
        // Arrange
        when(joinRequests.save(any(JoinRequest.class)))
                .thenThrow(new JoinRequestModifiedConcurrentlyException(request.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        JoinRequestApproval approval = useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), request.id()));

        // Assert
        assertThat(transactions.opened()).isEqualTo(2);
        verify(notifier, times(1)).memberApproved(association.id(), approval.member().id());
    }

    @Test
    void aFailingNotifierNeverUndoesTheApproval() {
        // Arrange
        doThrow(new IllegalStateException("mail down")).when(notifier).memberApproved(any(), any());

        // Act
        JoinRequestApproval approval = useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), request.id()));

        // Assert
        assertThat(approval.request().status()).isEqualTo(JoinRequestStatus.APPROVED);
        verify(members).save(approval.member());
    }

    @Test
    void provisionsTheNewMembersCredentialsAfterTheCommit() {
        // Arrange
        ApproveJoinRequestCommand command = new ApproveJoinRequestCommand(Data.actor(admin), request.id());

        // Act
        JoinRequestApproval approval = useCase.execute(command);

        // Assert
        verify(provisioner).provision(association.id(), approval.member().id(), EmailAddress.of("rita@example.com"));
    }

    @Test
    void aFailingProvisionerNeverUndoesTheApprovalNorStopsTheNotification() {
        // Arrange
        doThrow(new IllegalStateException("db down")).when(provisioner).provision(any(), any(), any());

        // Act
        JoinRequestApproval approval = useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), request.id()));

        // Assert
        assertThat(approval.request().status()).isEqualTo(JoinRequestStatus.APPROVED);
        verify(notifier).memberApproved(association.id(), approval.member().id());
    }

    @Test
    void nothingIsProvisionedWhenTheApprovalFails() {
        // Arrange
        when(members.save(any(Member.class))).thenThrow(new MemberEmailAlreadyUsedException());
        Executable act = () -> useCase.execute(new ApproveJoinRequestCommand(Data.actor(admin), request.id()));

        // Act
        assertThrows(MemberEmailAlreadyUsedException.class, act);

        // Assert
        verifyNoInteractions(provisioner);
    }
}
