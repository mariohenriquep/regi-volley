package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RejectJoinRequestCommand;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.JoinRequestNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.port.Notifier;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejectJoinRequestServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private JoinRequestRepository joinRequests;
    @Mock
    private Notifier notifier;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private JoinRequest request;
    private RejectJoinRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        request = JoinRequestFactory.create(association.id(), ContactDetails.of("Rita Costa", EmailAddress.of("rita@example.com"),
                PhoneNumber.of("912345678")), true, "2026-01", Data.CLOCK);
        useCase = new RejectJoinRequestService(members, joinRequests, transactions, notifier, Data.CLOCK);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(joinRequests.findById(association.id(), request.id())).thenReturn(Optional.of(request));
        lenient().when(joinRequests.save(any(JoinRequest.class))).thenAnswer(returnsFirstArg());
    }

    @Test
    void rejectingStoresTheDecisionWithItsReasonAndNotifiesWithTheRequestIdOnly() {
        // Arrange
        RejectJoinRequestCommand command = new RejectJoinRequestCommand(Data.actor(admin), request.id(), "Group is full");

        // Act
        JoinRequest rejected = useCase.execute(command);

        // Assert
        assertThat(rejected.status()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(rejected.decidedBy()).hasValue(admin.id());
        assertThat(rejected.rejectionReason()).hasValue("Group is full");
        verify(joinRequests).save(rejected);
        verify(notifier).joinRequestRejected(association.id(), request.id());
    }

    @Test
    void theReasonIsOptional() {
        // Arrange
        RejectJoinRequestCommand command = new RejectJoinRequestCommand(Data.actor(admin), request.id(), null);

        // Act
        JoinRequest rejected = useCase.execute(command);

        // Assert
        assertThat(rejected.rejectionReason()).isEmpty();
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member plain = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> useCase.execute(new RejectJoinRequestCommand(Data.actor(plain), request.id(), null));

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
        Executable act = () -> useCase.execute(new RejectJoinRequestCommand(Data.actor(admin), foreign.id(), null));

        // Act
        assertThrows(JoinRequestNotFoundException.class, act);

        // Assert
        verifyNoInteractions(notifier);
    }

    @Test
    void aDecidedRequestCannotBeRejected() {
        // Arrange
        JoinRequest approved = request.approve(admin.id(), Data.CLOCK);
        when(joinRequests.findById(association.id(), request.id())).thenReturn(Optional.of(approved));
        Executable act = () -> useCase.execute(new RejectJoinRequestCommand(Data.actor(admin), request.id(), null));

        // Act
        assertThrows(InvalidJoinRequestStatusTransitionException.class, act);

        // Assert
        verify(joinRequests, never()).save(any(JoinRequest.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void retriesInANewTransactionAndNotifiesOnlyOnce() {
        // Arrange
        when(joinRequests.save(any(JoinRequest.class)))
                .thenThrow(new JoinRequestModifiedConcurrentlyException(request.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        useCase.execute(new RejectJoinRequestCommand(Data.actor(admin), request.id(), null));

        // Assert
        assertThat(transactions.opened()).isEqualTo(2);
        verify(notifier, times(1)).joinRequestRejected(association.id(), request.id());
    }
}
