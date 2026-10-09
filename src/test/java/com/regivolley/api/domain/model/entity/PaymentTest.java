package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {
    private static final Instant NOW = Instant.parse("2026-10-12T22:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MemberId ADMIN = MemberId.generate();
    private static final LocalDate PAID_ON = LocalDate.parse("2026-10-10");

    private final AssociationId association = AssociationId.generate();
    private final Subscription subscription = SubscriptionFactory.create(
            PlanFactory.create(association, "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null),
            MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());

    @Test
    void equalityIsByIdAndToStringPrintsIdsOnly() {
        // Arrange
        Payment payment = PaymentFactory.create(subscription, Money.ofCents(1500), PAID_ON, PaymentMethod.CASH, ADMIN, CLOCK);
        Payment same = PaymentFactory.reconstitute(payment.id(), association, subscription.id(), Money.ofCents(1), PAID_ON,
                PaymentMethod.CASH, ADMIN, NOW, null);
        Payment other = PaymentFactory.create(subscription, Money.ofCents(1500), PAID_ON, PaymentMethod.CASH, ADMIN, CLOCK);

        // Act
        String text = payment.toString();

        // Assert
        assertThat(payment).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other).isNotEqualTo("x");
        assertThat(text).contains(payment.id().toString());
    }
}
