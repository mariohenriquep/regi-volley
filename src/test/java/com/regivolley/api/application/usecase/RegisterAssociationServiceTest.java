package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.result.AssociationRegistered;
import com.regivolley.api.domain.exception.AssociationModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAssociationServiceTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private MemberRepository members;

    private final DirectTransactions transactions = new DirectTransactions();

    private RegisterAssociationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterAssociationService(associations, members, transactions, Data.CLOCK);
        lenient().when(associations.save(any(Association.class))).thenAnswer(returnsFirstArg());
        lenient().when(members.save(any(Member.class))).thenAnswer(returnsFirstArg());
    }

    private RegisterAssociationCommand command(String shortName, boolean consent) {
        return new RegisterAssociationCommand("Volley Club", shortName, "123456789", "Lisbon", "info@volley.example",
                List.of("Beginner", "Intermediate", "Advanced"), "Ana Silva", "ana@example.com", "912345678",
                consent, "2026-01");
    }

    @Test
    void registersTheAssociationAndMakesTheFounderAnActiveAdministratorAtTheEntryLevel() {
        // Arrange
        RegisterAssociationCommand command = command("volley-club", true);

        // Act
        AssociationRegistered registered = useCase.execute(command);

        // Assert
        ArgumentCaptor<Association> savedAssociation = ArgumentCaptor.forClass(Association.class);
        ArgumentCaptor<Member> savedFounder = ArgumentCaptor.forClass(Member.class);
        var order = inOrder(associations, members);
        order.verify(associations).save(savedAssociation.capture());
        order.verify(members).save(savedFounder.capture());
        Association association = savedAssociation.getValue();
        Member founder = savedFounder.getValue();
        assertThat(registered.associationId()).isEqualTo(association.id());
        assertThat(registered.founderId()).isEqualTo(founder.id());
        assertThat(association.shortName()).isEqualTo(ShortName.of("volley-club"));
        assertThat(association.levels()).extracting(level -> level.name()).containsExactly("Beginner", "Intermediate", "Advanced");
        assertThat(founder.associationId()).isEqualTo(association.id());
        assertThat(founder.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(founder.roles()).containsExactlyInAnyOrder(MemberRole.ADMIN, MemberRole.MEMBER);
        assertThat(founder.levelId()).isEqualTo(association.entryLevelId());
        assertThat(founder.consent().givenAt()).isEqualTo(Data.NOW);
        assertThat(founder.consent().policyVersion()).isEqualTo("2026-01");
        assertThat(founder.joinedAt()).isEqualTo(Data.NOW);
        assertThat(transactions.opened()).isEqualTo(1);
    }

    @Test
    void aTakenShortNameIsRejectedAndNothingIsStored() {
        // Arrange
        when(associations.existsByShortName(ShortName.of("volley-club"))).thenReturn(true);
        Executable act = () -> useCase.execute(command("volley-club", true));

        // Act
        ShortNameAlreadyTakenException ex = assertThrows(ShortNameAlreadyTakenException.class, act);

        // Assert
        assertThat(ex.shortName()).isEqualTo(ShortName.of("volley-club"));
        verify(associations, never()).save(any(Association.class));
        verifyNoInteractions(members);
    }

    @Test
    void aShortNameTakenByARaceIsStillRejectedByTheDatabaseConstraint() {
        // Arrange
        when(associations.save(any(Association.class))).thenThrow(new ShortNameAlreadyTakenException(ShortName.of("volley-club")));
        Executable act = () -> useCase.execute(command("volley-club", true));

        // Act
        assertThrows(ShortNameAlreadyTakenException.class, act);

        // Assert
        verifyNoInteractions(members);
        assertThat(transactions.opened()).isEqualTo(1);
    }

    @Test
    void theConsentIsMandatoryAndNothingIsStoredWithoutIt() {
        // Arrange
        Executable act = () -> useCase.execute(command("volley-club", false));

        // Act
        assertThrows(ConsentRequiredException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
        verifyNoInteractions(members);
    }

    @Test
    void invalidFounderContactDataIsRejectedBeforeAnythingIsStored() {
        // Arrange
        RegisterAssociationCommand noEmail = new RegisterAssociationCommand("Volley Club", "volley-club", null, "Lisbon",
                "info@volley.example", List.of("Beginner"), "Ana", "not-an-email", "912345678", true, "2026-01");
        Executable act = () -> useCase.execute(noEmail);

        // Act
        assertThrows(InvalidFieldException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
        verifyNoInteractions(members);
    }

    @Test
    void anInvalidShortNameIsRejected() {
        // Arrange
        Executable act = () -> useCase.execute(command("Not Valid!", true));

        // Act
        assertThrows(InvalidFieldException.class, act);

        // Assert
        verify(associations, never()).save(any(Association.class));
    }

    @Test
    void retriesInANewTransactionOnAConflict() {
        // Arrange
        when(associations.save(any(Association.class)))
                .thenThrow(new AssociationModifiedConcurrentlyException(com.regivolley.api.domain.model.valueobject.AssociationId.generate()))
                .thenAnswer(returnsFirstArg());

        // Act
        AssociationRegistered registered = useCase.execute(command("volley-club", true));

        // Assert
        assertThat(registered.associationId()).isNotNull();
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
