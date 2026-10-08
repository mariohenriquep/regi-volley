package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MarkSubscriptionOverdueCommand;
import com.regivolley.api.domain.exception.InvalidPaymentStatusTransitionException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RN-18: an administrator marks an unpaid subscription as overdue, which stops its member from booking. */
@ExtendWith(MockitoExtension.class)
class MarkSubscriptionOverdueServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private SubscriptionRepository subscriptions;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Subscription pending;
    private MarkSubscriptionOverdueUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        Plan plan = Plan.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        pending = Subscription.create(plan, Data.member(association).id(), LocalDate.parse("2026-10-01"), List.of());
        useCase = new MarkSubscriptionOverdueService(members, subscriptions, transactions);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(subscriptions.findById(association.id(), pending.id())).thenReturn(Optional.of(pending));
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
    }

    private MarkSubscriptionOverdueCommand mark(SubscriptionId id) {
        return new MarkSubscriptionOverdueCommand(Data.actor(admin), id);
    }

    @Test
    void aPendingSubscriptionBecomesOverdueAndIsStored() {
        // Arrange
        MarkSubscriptionOverdueCommand command = mark(pending.id());

        // Act
        Subscription overdue = useCase.execute(command);

        // Assert
        assertThat(overdue.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
        verify(subscriptions).save(overdue);
    }

    @Test
    void aPaidOrAlreadyOverdueSubscriptionCannotBeMarked() {
        // Arrange
        Executable paid = () -> {
            when(subscriptions.findById(association.id(), pending.id())).thenReturn(Optional.of(pending.markPaid()));
            useCase.execute(mark(pending.id()));
        };
        Executable twice = () -> {
            when(subscriptions.findById(association.id(), pending.id())).thenReturn(Optional.of(pending.markOverdue()));
            useCase.execute(mark(pending.id()));
        };

        // Act
        assertThrows(InvalidPaymentStatusTransitionException.class, paid);
        assertThrows(InvalidPaymentStatusTransitionException.class, twice);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new MarkSubscriptionOverdueCommand(Data.actor(coach), pending.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void aSubscriptionOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> useCase.execute(mark(SubscriptionId.generate()));

        // Act
        assertThrows(SubscriptionNotFoundException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void retriesInANewTransactionOnAConflict() {
        // Arrange
        when(subscriptions.save(any(Subscription.class)))
                .thenThrow(new SubscriptionModifiedConcurrentlyException(pending.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        Subscription overdue = useCase.execute(mark(pending.id()));

        // Assert
        assertThat(overdue.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
