package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.result.PaymentRecorded;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.PaymentExceedsOutstandingException;
import com.regivolley.api.domain.exception.PaymentModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-21, RN-17, RN-18: a EUR 30.00 monthly plan; partial payments are allowed, overpayments are not. */
@ExtendWith(MockitoExtension.class)
class RecordPaymentServiceTest {

    private static final LocalDate PAID_ON = LocalDate.parse("2026-10-10");

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
    private Subscription subscription;
    private RecordPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        plan = Plan.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        subscription = Subscription.create(plan, Data.member(association).id(), LocalDate.parse("2026-10-01"), List.of());
        useCase = new RecordPaymentService(members, subscriptions, payments, transactions, Data.CLOCK);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.of(subscription));
        lenient().when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of());
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
        lenient().when(payments.add(any(Payment.class))).thenAnswer(returnsFirstArg());
    }

    private RecordPaymentCommand pay(long cents) {
        return new RecordPaymentCommand(Data.actor(admin), subscription.id(), Money.ofCents(cents), PAID_ON, PaymentMethod.TRANSFER);
    }

    @Test
    void thePriceInFullMarksTheSubscriptionPaidAndRecordsTheAudit() {
        // Arrange
        RecordPaymentCommand command = pay(3000);

        // Act
        PaymentRecorded recorded = useCase.execute(command);

        // Assert
        assertThat(recorded.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(recorded.outstanding()).isEqualTo(Money.ofCents(0));
        Payment payment = recorded.payment();
        assertThat(payment.subscriptionId()).isEqualTo(subscription.id());
        assertThat(payment.amount()).isEqualTo(Money.ofCents(3000));
        assertThat(payment.paidOn()).isEqualTo(PAID_ON);
        assertThat(payment.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(payment.recordedBy()).isEqualTo(admin.id());
        assertThat(payment.recordedAt()).isEqualTo(Data.NOW);
        assertThat(payment.associationId()).isEqualTo(association.id());
    }

    @Test
    void theSubscriptionIsSavedBeforeThePaymentSoConcurrentPaymentsQueueOnIt() {
        // Arrange
        RecordPaymentCommand command = pay(3000);

        // Act
        PaymentRecorded recorded = useCase.execute(command);

        // Assert
        var order = inOrder(subscriptions, payments);
        order.verify(subscriptions).save(recorded.subscription());
        order.verify(payments).add(recorded.payment());
    }

    @Test
    void aPartialPaymentLeavesTheSubscriptionPendingWithWhatIsStillDue() {
        // Arrange
        RecordPaymentCommand command = pay(1000);

        // Act
        PaymentRecorded recorded = useCase.execute(command);

        // Assert
        assertThat(recorded.subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(recorded.outstanding()).isEqualTo(Money.ofCents(2000));
        verify(subscriptions).save(recorded.subscription());
    }

    @Test
    void thePaymentThatCompletesThePriceMarksItPaid() {
        // Arrange
        Payment earlier = Payment.record(subscription, Money.ofCents(1000), PAID_ON, PaymentMethod.CASH, admin.id(), Data.CLOCK);
        when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of(earlier));

        // Act
        PaymentRecorded recorded = useCase.execute(pay(2000));

        // Assert
        assertThat(recorded.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void anOverdueSubscriptionPaidInFullBecomesPaid() {
        // Arrange
        when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.of(subscription.markOverdue()));

        // Act
        PaymentRecorded recorded = useCase.execute(pay(3000));

        // Assert
        assertThat(recorded.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aPaymentAboveWhatIsDueIsRefusedAndNothingIsStored() {
        // Arrange
        Executable act = () -> useCase.execute(pay(3001));

        // Act
        PaymentExceedsOutstandingException ex = assertThrows(PaymentExceedsOutstandingException.class, act);

        // Assert
        assertThat(ex.outstanding()).isEqualTo(Money.ofCents(3000));
        verify(subscriptions, never()).save(any(Subscription.class));
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aSettledSubscriptionTakesNoMorePayments() {
        // Arrange
        Payment full = Payment.record(subscription, Money.ofCents(3000), PAID_ON, PaymentMethod.CASH, admin.id(), Data.CLOCK);
        when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of(full));
        Executable act = () -> useCase.execute(pay(1));

        // Act
        assertThrows(PaymentExceedsOutstandingException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aZeroAmountIsRefused() {
        // Arrange
        Executable act = () -> useCase.execute(pay(0));

        // Act
        assertThrows(InvalidFieldException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new RecordPaymentCommand(Data.actor(coach), subscription.id(),
                Money.ofCents(3000), PAID_ON, PaymentMethod.CASH));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void aSubscriptionOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> useCase.execute(new RecordPaymentCommand(Data.actor(admin), SubscriptionId.generate(),
                Money.ofCents(3000), PAID_ON, PaymentMethod.CASH));

        // Act
        assertThrows(SubscriptionNotFoundException.class, act);

        // Assert
        verify(payments, never()).add(any(Payment.class));
    }

    @Test
    void editingThePlansPriceDoesNotChangeWhatAnExistingSubscriptionOwes() {
        // Arrange - the plan may cost 50.00 by now (the service does not even look at it): the subscription was sold at 30.00
        Plan repriced = plan.edit(plan.name(), plan.terms(), Money.ofCents(5000), null);

        // Act
        PaymentRecorded recorded = useCase.execute(pay(3000));

        // Assert
        assertThat(recorded.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(recorded.subscription().price()).isEqualTo(Money.ofCents(3000));
        assertThat(repriced.price()).isEqualTo(Money.ofCents(5000));
        assertThat(recorded.outstanding()).isEqualTo(Money.ofCents(0));
    }

    @Test
    void retriesInANewTransactionWhenTheSubscriptionChangedMeanwhile() {
        // Arrange
        when(subscriptions.save(any(Subscription.class)))
                .thenThrow(new SubscriptionModifiedConcurrentlyException(subscription.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        PaymentRecorded recorded = useCase.execute(pay(3000));

        // Assert
        assertThat(recorded.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(transactions.opened()).isEqualTo(2);
        verify(payments).add(recorded.payment());
    }

    @Test
    void retriesWhenThePaymentRowIsRefusedAndRereadsThePayments() {
        // Arrange
        when(payments.add(any(Payment.class)))
                .thenThrow(new PaymentModifiedConcurrentlyException(com.regivolley.api.domain.model.valueobject.PaymentId.generate()))
                .thenAnswer(returnsFirstArg());

        // Act
        useCase.execute(pay(3000));

        // Assert
        assertThat(transactions.opened()).isEqualTo(2);
        verify(payments, org.mockito.Mockito.times(2)).findBySubscription(association.id(), subscription.id());
    }
}
