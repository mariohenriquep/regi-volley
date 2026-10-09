package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidPaymentException;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
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

class PaymentFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-12T22:30:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final MemberId ADMIN = MemberId.generate();

    private static final LocalDate PAID_ON = LocalDate.parse("2026-10-10");

    private final AssociationId association = AssociationId.generate();

    private final Subscription subscription = SubscriptionFactory.create(
            PlanFactory.create(association, "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null),
            MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());

    @Test
    void recordsAPaymentAgainstASubscriptionWithAuditAndNoReversal() {
        // Arrange
        // (subscription from the fields)

        // Act
        Payment payment = PaymentFactory.create(subscription, Money.ofCents(1500), PAID_ON, PaymentMethod.MB_WAY, ADMIN, CLOCK);

        // Assert
        assertThat(payment.id()).isNotNull();
        assertThat(payment.associationId()).isEqualTo(association);
        assertThat(payment.subscriptionId()).isEqualTo(subscription.id());
        assertThat(payment.amount()).isEqualTo(Money.ofCents(1500));
        assertThat(payment.paidOn()).isEqualTo(PAID_ON);
        assertThat(payment.method()).isEqualTo(PaymentMethod.MB_WAY);
        assertThat(payment.recordedBy()).isEqualTo(ADMIN);
        assertThat(payment.recordedAt()).isEqualTo(NOW);
        assertThat(payment.reversalOf()).isEmpty();
        assertThat(payment.isReversal()).isFalse();
        assertThat(payment.signedCents()).isEqualTo(1500);
    }

    @Test
    void aPaymentOfZeroIsRejected() {
        // Arrange
        Executable act = () -> PaymentFactory.create(subscription, Money.ofCents(0), PAID_ON, PaymentMethod.CASH, ADMIN, CLOCK);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("amount");
    }

    @Test
    void reversingCreatesANewNegativePaymentLinkedToTheOriginal() {
        // Arrange
        Payment original = PaymentFactory.create(subscription, Money.ofCents(1500), PAID_ON, PaymentMethod.TRANSFER, ADMIN, CLOCK);
        MemberId reverser = MemberId.generate();
        Clock later = Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC);

        // Act
        Payment reversal = PaymentFactory.createReversal(original, reverser, later);

        // Assert
        assertThat(reversal.id()).isNotEqualTo(original.id());
        assertThat(reversal.reversalOf()).hasValue(original.id());
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.amount()).isEqualTo(original.amount());
        assertThat(reversal.signedCents()).isEqualTo(-1500);
        assertThat(reversal.subscriptionId()).isEqualTo(original.subscriptionId());
        assertThat(reversal.associationId()).isEqualTo(association);
        assertThat(reversal.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(reversal.recordedBy()).isEqualTo(reverser);
        assertThat(reversal.recordedAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(original.reversalOf()).isEmpty();
    }

    @Test
    void theReversalIsDatedOnTheLisbonDayItWasMade() {
        // Arrange - 23:30 UTC in July is already 00:30 of the next day in Lisbon (summer time)
        Payment original = PaymentFactory.create(subscription, Money.ofCents(1500), PAID_ON, PaymentMethod.CASH, ADMIN, CLOCK);
        Clock summerLate = Clock.fixed(Instant.parse("2026-07-01T23:30:00Z"), ZoneOffset.UTC);

        // Act
        Payment reversal = PaymentFactory.createReversal(original, ADMIN, summerLate);

        // Assert
        assertThat(reversal.paidOn()).isEqualTo(LocalDate.parse("2026-07-02"));
    }

    @Test
    void aReversalCannotBeReversed() {
        // Arrange
        Payment reversal = PaymentFactory.createReversal(
                PaymentFactory.create(subscription, Money.ofCents(1500), PAID_ON, PaymentMethod.CASH, ADMIN, CLOCK), ADMIN, CLOCK);
        Executable act = () -> PaymentFactory.createReversal(reversal, ADMIN, CLOCK);

        // Act
        InvalidPaymentException ex = assertThrows(InvalidPaymentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("reversal");
    }

    @Test
    void reconstructRechecksTheInvariants() {
        // Arrange
        PaymentId id = PaymentId.generate();
        Executable zero = () -> PaymentFactory.reconstitute(id, association, subscription.id(), Money.ofCents(0), PAID_ON,
                PaymentMethod.CASH, ADMIN, NOW, null);
        Executable selfReversal = () -> PaymentFactory.reconstitute(id, association, subscription.id(), Money.ofCents(5), PAID_ON,
                PaymentMethod.CASH, ADMIN, NOW, id);
        Executable noMethod = () -> PaymentFactory.reconstitute(id, association, subscription.id(), Money.ofCents(5), PAID_ON,
                null, ADMIN, NOW, null);
        Executable noSubscription = () -> PaymentFactory.reconstitute(id, association, (SubscriptionId) null, Money.ofCents(5), PAID_ON,
                PaymentMethod.CASH, ADMIN, NOW, null);

        // Act
        InvalidFieldException zeroEx = assertThrows(InvalidFieldException.class, zero);
        InvalidPaymentException selfEx = assertThrows(InvalidPaymentException.class, selfReversal);
        NullPointerException methodEx = assertThrows(NullPointerException.class, noMethod);
        NullPointerException subscriptionEx = assertThrows(NullPointerException.class, noSubscription);

        // Assert
        assertThat(zeroEx.field()).isEqualTo("amount");
        assertThat(selfEx.getMessage()).contains("itself");
        assertThat(methodEx.getMessage()).contains("method");
        assertThat(subscriptionEx.getMessage()).contains("subscriptionId");
    }

    @Test
    void aPaymentCannotBeDatedInTheFuture() {
        // Arrange - NOW is 22:30 UTC on 12 Oct 2026, so it is still the 12th in Lisbon (UTC+1 -> 23:30)
        Executable tomorrow = () -> PaymentFactory.create(subscription, Money.ofCents(1500), LocalDate.parse("2026-10-13"),
                PaymentMethod.CASH, ADMIN, CLOCK);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, tomorrow);

        // Assert
        assertThat(ex.field()).isEqualTo("payment date");
    }

    @Test
    void todayInLisbonIsNotTheFutureEvenWhenUtcIsStillYesterday() {
        // Arrange - 23:30 UTC on 12 Jul is 00:30 on the 13th in Lisbon (summer time)
        Clock lisbonAlreadyTomorrow = Clock.fixed(Instant.parse("2026-07-12T23:30:00Z"), ZoneOffset.UTC);

        // Act
        Payment payment = PaymentFactory.create(subscription, Money.ofCents(1500), LocalDate.parse("2026-07-13"),
                PaymentMethod.CASH, ADMIN, lisbonAlreadyTomorrow);

        // Assert
        assertThat(payment.paidOn()).isEqualTo(LocalDate.parse("2026-07-13"));
    }
}
