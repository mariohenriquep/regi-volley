package com.regivolley.api.domain.service;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.PaymentExceedsOutstandingException;
import com.regivolley.api.domain.exception.PaymentNotReversibleException;
import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentLedgerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    private static final MemberId ADMIN = MemberId.generate();
    private static final LocalDate DAY = LocalDate.parse("2026-10-12");
    private static final Money PRICE = Money.ofCents(3000);

    private final AssociationId association = AssociationId.generate();
    private final Plan plan = PlanFactory.create(association, "Monthly", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);
    private final Subscription subscription = SubscriptionFactory.create(plan, MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());

    private PaymentLedger ledger(Payment... payments) {
        return PaymentLedger.of(subscription, List.of(payments));
    }

    private Payment pay(long cents) {
        return PaymentFactory.create(subscription, Money.ofCents(cents), DAY, PaymentMethod.CASH, ADMIN, CLOCK);
    }

    @Test
    void aSubscriptionWithoutPaymentsOwesTheFullPrice() {
        // Arrange
        PaymentLedger ledger = ledger();

        // Act
        Money paid = ledger.paid();

        // Assert
        assertThat(paid).isEqualTo(Money.ofCents(0));
        assertThat(ledger.outstanding()).isEqualTo(PRICE);
        assertThat(ledger.isSettled()).isFalse();
    }

    @Test
    void paymentsAddUpAndReversalsTakeAwayWhatTheyUndo() {
        // Arrange
        Payment first = pay(1000);
        Payment second = pay(500);
        Payment reversal = PaymentFactory.createReversal(second, ADMIN, CLOCK);

        // Act
        PaymentLedger ledger = ledger(first, second, reversal);

        // Assert
        assertThat(ledger.paid()).isEqualTo(Money.ofCents(1000));
        assertThat(ledger.outstanding()).isEqualTo(Money.ofCents(2000));
    }

    @Test
    void recordsAPartialPaymentWithinWhatIsDue() {
        // Arrange
        PaymentLedger ledger = ledger(pay(1000));

        // Act
        Payment payment = ledger.record(Money.ofCents(2000), DAY, PaymentMethod.TRANSFER, ADMIN, CLOCK);

        // Assert
        assertThat(payment.amount()).isEqualTo(Money.ofCents(2000));
        assertThat(payment.subscriptionId()).isEqualTo(subscription.id());
        assertThat(ledger.with(payment).isSettled()).isTrue();
    }

    @Test
    void refusesAPaymentAboveWhatIsDue() {
        // Arrange
        PaymentLedger ledger = ledger(pay(1000));
        Executable act = () -> ledger.record(Money.ofCents(2001), DAY, PaymentMethod.CASH, ADMIN, CLOCK);

        // Act
        PaymentExceedsOutstandingException ex = assertThrows(PaymentExceedsOutstandingException.class, act);

        // Assert
        assertThat(ex.outstanding()).isEqualTo(Money.ofCents(2000));
    }

    @Test
    void refusesAnyPaymentOnASettledSubscription() {
        // Arrange
        PaymentLedger ledger = ledger(pay(3000));
        Executable act = () -> ledger.record(Money.ofCents(1), DAY, PaymentMethod.CASH, ADMIN, CLOCK);

        // Act
        PaymentExceedsOutstandingException ex = assertThrows(PaymentExceedsOutstandingException.class, act);

        // Assert
        assertThat(ex.outstanding()).isEqualTo(Money.ofCents(0));
    }

    @Test
    void refusesAZeroPayment() {
        // Arrange
        Executable act = () -> ledger().record(Money.ofCents(0), DAY, PaymentMethod.CASH, ADMIN, CLOCK);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("amount");
    }

    @Test
    void reversesAPaymentOnce() {
        // Arrange
        Payment payment = pay(3000);
        PaymentLedger ledger = ledger(payment);

        // Act
        Payment reversal = ledger.reverse(payment.id(), ADMIN, CLOCK);

        // Assert
        assertThat(reversal.reversalOf()).hasValue(payment.id());
        assertThat(ledger.with(reversal).paid()).isEqualTo(Money.ofCents(0));
    }

    @Test
    void refusesToReverseAPaymentTwice() {
        // Arrange
        Payment payment = pay(3000);
        PaymentLedger ledger = ledger(payment, PaymentFactory.createReversal(payment, ADMIN, CLOCK));
        Executable act = () -> ledger.reverse(payment.id(), ADMIN, CLOCK);

        // Act
        PaymentNotReversibleException ex = assertThrows(PaymentNotReversibleException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("already");
    }

    @Test
    void refusesToReverseAReversal() {
        // Arrange
        Payment reversal = PaymentFactory.createReversal(pay(3000), ADMIN, CLOCK);
        PaymentLedger ledger = ledger(pay(3000), reversal);
        Executable act = () -> ledger.reverse(reversal.id(), ADMIN, CLOCK);

        // Act
        PaymentNotReversibleException ex = assertThrows(PaymentNotReversibleException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("reversal");
    }

    @Test
    void refusesToReverseAPaymentOfAnotherSubscription() {
        // Arrange
        PaymentLedger ledger = ledger(pay(3000));
        Executable act = () -> ledger.reverse(PaymentId.generate(), ADMIN, CLOCK);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("subscription");
    }

    @Test
    void refusesPaymentsOfAnotherSubscriptionOrTenant() {
        // Arrange
        Subscription other = SubscriptionFactory.create(plan, MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());
        Payment foreign = PaymentFactory.create(other, Money.ofCents(100), DAY, PaymentMethod.CASH, ADMIN, CLOCK);
        Executable act = () -> PaymentLedger.of(subscription, List.of(foreign));

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("subscription");
    }

    @Test
    void settlingMovesAPendingSubscriptionToPaidOnlyWhenNothingIsDue() {
        // Arrange
        PaymentLedger partial = ledger(pay(1000));
        PaymentLedger full = ledger(pay(3000));

        // Act
        Subscription stillPending = partial.settle(subscription);
        Subscription paid = full.settle(subscription);

        // Assert
        assertThat(stillPending.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void settlingAnOverdueSubscriptionInFullMakesItPaid() {
        // Arrange
        Subscription overdue = subscription.markOverdue();

        // Act
        Subscription paid = ledger(pay(3000)).settle(overdue);

        // Assert
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void settlingReopensAPaidSubscriptionThatIsOwedAgain() {
        // Arrange
        Payment payment = pay(3000);
        Subscription paid = subscription.markPaid();
        PaymentLedger afterReversal = ledger(payment, PaymentFactory.createReversal(payment, ADMIN, CLOCK));

        // Act
        Subscription reopened = afterReversal.settle(paid);

        // Assert
        assertThat(reopened.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void settlingLeavesAnOverdueSubscriptionAloneWhileMoneyIsStillDue() {
        // Arrange
        Subscription overdue = subscription.markOverdue();

        // Act
        Subscription same = ledger(pay(1000)).settle(overdue);

        // Assert
        assertThat(same.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    void aFreePlanIsSettledFromTheStart() {
        // Arrange
        Plan freePlan = PlanFactory.create(association, "Trial", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(0), null);
        Subscription trial = SubscriptionFactory.create(freePlan, MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());
        PaymentLedger free = PaymentLedger.of(trial, List.of());

        // Act
        Subscription paid = free.settle(trial);

        // Assert
        assertThat(free.isSettled()).isTrue();
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void whatIsDueFollowsThePriceTheSubscriptionWasSoldAtNotTheCurrentPlanPrice() {
        // Arrange
        Plan repriced = plan.edit(plan.name(), plan.terms(), Money.ofCents(5000), null);
        Subscription renewal = SubscriptionFactory.create(repriced, MemberId.generate(), LocalDate.parse("2026-11-01"), List.of());

        // Act
        PaymentLedger oldPrice = PaymentLedger.of(subscription, List.of());
        PaymentLedger newPrice = PaymentLedger.of(renewal, List.of());

        // Assert
        assertThat(oldPrice.outstanding()).isEqualTo(Money.ofCents(3000));
        assertThat(newPrice.outstanding()).isEqualTo(Money.ofCents(5000));
    }

    @Test
    void settlingRefusesAnotherSubscription() {
        // Arrange
        Subscription other = SubscriptionFactory.create(plan, MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());
        Executable act = () -> ledger().settle(other);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("subscription");
    }
}
