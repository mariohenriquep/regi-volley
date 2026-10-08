package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
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
class SubmitJoinRequestServiceTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private MemberRepository members;
    @Mock
    private JoinRequestRepository joinRequests;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private SubmitJoinRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        useCase = new SubmitJoinRequestService(associations, members, joinRequests, transactions, Data.CLOCK);
        lenient().when(associations.findByShortName(association.shortName())).thenReturn(Optional.of(association));
        lenient().when(joinRequests.save(any(JoinRequest.class))).thenAnswer(returnsFirstArg());
    }

    private SubmitJoinRequestCommand command(String shortName, String email, boolean consent) {
        return new SubmitJoinRequestCommand(shortName, "Rita Costa", email, "912345678", consent, "2026-01");
    }

    @Test
    void storesAPendingRequestWithTheConsentStampedByTheServerClock() {
        // Arrange
        SubmitJoinRequestCommand command = command(association.shortName().value(), "rita@example.com", true);

        // Act
        JoinRequestSubmitted submitted = useCase.execute(command);

        // Assert
        ArgumentCaptor<JoinRequest> saved = ArgumentCaptor.forClass(JoinRequest.class);
        verify(joinRequests).save(saved.capture());
        JoinRequest request = saved.getValue();
        assertThat(submitted.requestId()).isEqualTo(request.id());
        assertThat(request.associationId()).isEqualTo(association.id());
        assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(request.email()).isEqualTo(EmailAddress.of("rita@example.com"));
        assertThat(request.consent().givenAt()).isEqualTo(Data.NOW);
        assertThat(request.consent().policyVersion()).isEqualTo("2026-01");
    }

    @Test
    void anUnknownShortNameIsNotFound() {
        // Arrange
        Executable act = () -> useCase.execute(command("no-such-club", "rita@example.com", true));

        // Act
        ShortNameNotFoundException ex = assertThrows(ShortNameNotFoundException.class, act);

        // Assert
        assertThat(ex.shortName()).isEqualTo(ShortName.of("no-such-club"));
        verify(joinRequests, never()).save(any(JoinRequest.class));
    }

    @Test
    void theConsentIsMandatory() {
        // Arrange
        Executable act = () -> useCase.execute(command(association.shortName().value(), "rita@example.com", false));

        // Act
        assertThrows(ConsentRequiredException.class, act);

        // Assert
        verify(joinRequests, never()).save(any(JoinRequest.class));
    }

    @Test
    void anEmailThatAlreadyBelongsToAMemberIsRejectedWithTheGenericMessage() {
        // Arrange
        Member existing = Data.member(association);
        when(members.findByEmail(association.id(), existing.email())).thenReturn(Optional.of(existing));
        Executable act = () -> useCase.execute(command(association.shortName().value(), existing.email().value(), true));

        // Act
        JoinRequestNotPossibleException ex = assertThrows(JoinRequestNotPossibleException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain(existing.email().value());
        verify(joinRequests, never()).save(any(JoinRequest.class));
    }

    @Test
    void anEmailWithAPendingRequestIsRejectedWithTheSameGenericMessage() {
        // Arrange
        EmailAddress email = EmailAddress.of("rita@example.com");
        JoinRequest pending = JoinRequestFactory.create(association.id(),
                com.regivolley.api.domain.model.valueobject.ContactDetails.of("Rita", email,
                        com.regivolley.api.domain.model.valueobject.PhoneNumber.of("912345678")), true, "2026-01", Data.CLOCK);
        when(joinRequests.findPendingByEmail(association.id(), email)).thenReturn(Optional.of(pending));
        when(members.findByEmail(association.id(), email)).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(command(association.shortName().value(), "rita@example.com", true));

        // Act
        JoinRequestNotPossibleException ex = assertThrows(JoinRequestNotPossibleException.class, act);

        // Assert
        assertThat(ex.getMessage()).isEqualTo(new JoinRequestNotPossibleException().getMessage());
        verify(joinRequests, never()).save(any(JoinRequest.class));
    }

    @Test
    void aRaceOnThePendingEmailSurfacesTheSameGenericRejection() {
        // Arrange
        when(joinRequests.save(any(JoinRequest.class))).thenThrow(new JoinRequestNotPossibleException());
        Executable act = () -> useCase.execute(command(association.shortName().value(), "rita@example.com", true));

        // Act
        assertThrows(JoinRequestNotPossibleException.class, act);

        // Assert
        assertThat(transactions.opened()).isEqualTo(1);
    }

    @Test
    void invalidContactDataIsRejected() {
        // Arrange
        Executable act = () -> useCase.execute(command(association.shortName().value(), "not-an-email", true));

        // Act
        assertThrows(InvalidFieldException.class, act);

        // Assert
        verify(joinRequests, never()).save(any(JoinRequest.class));
    }
}
