package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
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

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitJoinRequestServiceTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private MemberRepository members;
    @Mock
    private JoinRequestRepository joinRequests;
    @Mock
    private AttemptThrottle throttle;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private SubmitJoinRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        useCase = new SubmitJoinRequestService(associations, members, joinRequests, throttle, transactions, Data.CLOCK);
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
        assertThat(ex.shortName()).isEqualTo("no-such-club");
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
        JoinRequest pending = JoinRequestFactory.create(association.id(), com.regivolley.api.domain.model.valueobject.ContactDetails.of("Rita", email,
                        com.regivolley.api.domain.model.valueobject.PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-01", Data.CLOCK), Data.CLOCK);
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

    @Test
    void everyAttemptIsCountedAgainstTheAssociationAndEmailBeforeAnythingIsLookedUp() {
        // Arrange
        SubmitJoinRequestCommand command = command(association.shortName().value(), "Rita@Example.com", true);

        // Act
        useCase.execute(command);

        // Assert
        verify(throttle).checkJoinRequest(association.shortName().value(), "rita@example.com");
    }

    @Test
    void anEmailThatAskedTooOftenIsRefusedWithoutTouchingTheData() {
        // Arrange
        org.mockito.Mockito.doThrow(new RateLimitExceededException(Duration.ofHours(1)))
                .when(throttle).checkJoinRequest(association.shortName().value(), "rita@example.com");
        Executable act = () -> useCase.execute(command(association.shortName().value(), "rita@example.com", true));

        // Act
        assertThrows(RateLimitExceededException.class, act);

        // Assert
        verify(joinRequests, never()).save(any(JoinRequest.class));
        verify(associations, never()).findByShortName(any(ShortName.class));
    }

    @Test
    void theDuplicateOutcomesAreCountedLikeTheNewOne() {
        // Arrange
        Member existing = Data.member(association);
        when(members.findByEmail(association.id(), existing.email())).thenReturn(Optional.of(existing));
        Executable act = () -> useCase.execute(command(association.shortName().value(), existing.email().value(), true));

        // Act
        assertThrows(JoinRequestNotPossibleException.class, act);

        // Assert
        verify(throttle).checkJoinRequest(association.shortName().value(), existing.email().value());
    }

    @Test
    void aMissingConsentIsRefusedTheSameForANewAMemberAndAPendingAddressAndBeforeAnythingIsLookedUp() {
        // Arrange - threat model P1: the refusal must not depend on what is stored
        Member existing = Data.member(association);
        lenient().when(members.findByEmail(association.id(), existing.email())).thenReturn(Optional.of(existing));
        Executable newAddress = () -> useCase.execute(command(association.shortName().value(), "new@example.com", false));
        Executable memberAddress = () -> useCase.execute(command(association.shortName().value(), existing.email().value(), false));

        // Act
        ConsentRequiredException first = assertThrows(ConsentRequiredException.class, newAddress);
        ConsentRequiredException second = assertThrows(ConsentRequiredException.class, memberAddress);

        // Assert
        assertThat(second.getMessage()).isEqualTo(first.getMessage());
        verifyNoInteractions(throttle, associations, members, joinRequests);
    }

    @Test
    void aMissingPolicyVersionAndAnInvalidPhoneAreRefusedBeforeAnyLookupToo() {
        // Arrange
        Executable noPolicy = () -> useCase.execute(new SubmitJoinRequestCommand(association.shortName().value(), "Rita Costa",
                "rita@example.com", "912345678", true, " "));
        Executable badPhone = () -> useCase.execute(new SubmitJoinRequestCommand(association.shortName().value(), "Rita Costa",
                "rita@example.com", "12", true, "2026-01"));

        // Act
        InvalidFieldException policy = assertThrows(InvalidFieldException.class, noPolicy);
        InvalidFieldException phone = assertThrows(InvalidFieldException.class, badPhone);

        // Assert
        assertThat(policy.field()).isEqualTo("policy version");
        assertThat(phone.field()).isEqualTo("phone");
        verifyNoInteractions(throttle, associations, members, joinRequests);
    }

    @Test
    void aTextThatCannotBeAShortNameIsNotFoundLikeAnUnknownOne() {
        // Arrange
        Executable act = () -> useCase.execute(command("Not A Short Name!", "rita@example.com", true));

        // Act
        ShortNameNotFoundException ex = assertThrows(ShortNameNotFoundException.class, act);

        // Assert
        assertThat(ex.shortName()).isEqualTo("Not A Short Name!");
        verifyNoInteractions(throttle, associations, members, joinRequests);
    }
}
