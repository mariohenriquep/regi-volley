package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ReversePaymentCommand;
import com.regivolley.api.application.result.PaymentReversed;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.PaymentNotFoundException;
import com.regivolley.api.domain.exception.PaymentNotReversibleException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RN-19: a payment is never changed or deleted; it is reversed by a new payment that counts negatively. */
@ExtendWith(MockitoExtension.class)
class ReversePaymentServiceTest {

    private static final LocalDate PAID_ON = LocalDate.parse("2026-10-10");
    private static final Clock EARLIER = Clock.fixed(Data.NOW.minusSeconds(3600), java.time.ZoneOffset.UTC);

    @Mock
    private MemberRepository members;
    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private PaymentRepository payments;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Plan plan;
    private Subscription paid;
    private Payment payment;
    private ReversePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        plan = Plan.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        Subscription pending = Subscription.create(plan, Data.member(association).id(), LocalDate.parse("2026-10-01"), List.of());
        payment = Payment.record(pending, Money.ofCents(3000), PAID_ON, PaymentMethod.CASH, admin.id(), EARLIER);
        paid = pending.markPaid();
        useCase = new ReversePaymentService(members, subscriptions, payments, transactions, Data.CLOCK);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(subscriptions.findById(association.id(), paid.id())).thenReturn(Optional.of(paid));
        lenient().when(payments.findById(association.id(), payment.id())).thenReturn(Optional.of(payment));
        lenient().when(payments.findBySubscription(association.id(), paid.id())).thenReturn(List.of(payment));
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
        lenient().when(payments.add(any(Payment.class))).thenAnswer(returnsFirstArg());
    }

    private ReversePaymentCommand reverse(PaymentId id) {
        return new ReversePaymentCommand(Data.actor(admin), id);
    }

    @Test
    void reversingAddsANegativePaymentAndNeverTouchesTheOriginal() {
        // Arrange
        ReversePaymentCommand command = reverse(payment.id());

        // Act
        PaymentReversed reversed = useCase.execute(command);

        // Assert
        Payment reversal = reversed.reversal();
        assertThat(reversal.reversalOf()).hasValue(payment.id());
        assertThat(reversal.signedCents()).isEqualTo(-3000);
        assertThat(reversal.recordedBy()).isEqualTo(admin.id());
        assertThat(reversal.recordedAt()).isEqualTo(Data.NOW);
        verify(payments).add(reversal);
        verify(payments, never()).findById(association.id(), reversal.id());
        assertThat(payment.reversalOf()).isEmpty();
    }

    @Test
    void aFullyPaidSubscriptionGoesBackToPendingWithTheAmountDueAgain() {
        // Arrange
        ReversePaymentCommand command = reverse(payment.id());

        // Act
        PaymentReversed reversed = useCase.execute(command);

        // Assert
        assertThat(reversed.subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reversed.outstanding()).isEqualTo(Money.ofCents(3000));
        var order = inOrder(subscriptions, payments);
        order.verify(subscriptions).save(reversed.subscription());
        order.verify(payments).add(reversed.reversal());
    }

    @Test
    void reversingOneOfTwoPaymentsKeepsAPendingSubscriptionPending() {
        // Arrange
        Subscription pending = Subscription.create(plan, paid.memberId(), LocalDate.parse("2026-10-01"), List.of());
        Payment first = Payment.record(pending, Money.ofCents(1000), PAID_ON, PaymentMethod.CASH, admin.id(), EARLIER);
        Payment second = Payment.record(pending, Money.ofCents(500), PAID_ON, PaymentMethod.CASH, admin.id(), EARLIER);
        when(subscriptions.findById(association.id(), pending.id())).thenReturn(Optional.of(pending));
        when(payments.findById(association.id(), second.id())).thenReturn(Optional.of(second));
        when(payments.findBySubscription(association.id(), pending.id())).thenReturn(List.of(first, second));

        // Act
        PaymentReversed reversed = useCase.execute(reverse(second.id()));

        // Assert
        assertThat(reversed.subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reversed.outstanding()).isEqualTo(Money.ofCents(2000));
    }

    @Test
    void aPaymentAlreadyReversedCannotBeReversedAgain() {
        // Arrange
        when(payments.findBySubscription(association.id(), paid.id()))
                .thenReturn(List.of(payment, payment.reverse(admin.id(), EARLIER)));
        Executable act = () -> useCase.execute(reverse(payment.id()));

        // Act
        assertThrows(PaymentNotReversibleException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aReversalCannotBeReversed() {
        // Arrange
        Payment reversal = payment.reverse(admin.id(), EARLIER);
        when(payments.findById(association.id(), reversal.id())).thenReturn(Optional.of(reversal));
        when(payments.findBySubscription(association.id(), paid.id())).thenReturn(List.of(payment, reversal));
        Executable act = () -> useCase.execute(reverse(reversal.id()));

        // Act
        assertThrows(PaymentNotReversibleException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aPaymentOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> useCase.execute(reverse(PaymentId.generate()));

        // Act
        assertThrows(PaymentNotFoundException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ReversePaymentCommand(Data.actor(coach), payment.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void retriesInANewTransactionWhenTheSubscriptionChangedMeanwhile() {
        // Arrange
        when(subscriptions.save(any(Subscription.class)))
                .thenThrow(new SubscriptionModifiedConcurrentlyException(paid.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        PaymentReversed reversed = useCase.execute(reverse(payment.id()));

        // Assert
        assertThat(reversed.subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
