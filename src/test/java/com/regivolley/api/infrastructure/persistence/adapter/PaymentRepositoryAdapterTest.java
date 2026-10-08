package com.regivolley.api.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;
import com.regivolley.api.domain.exception.PaymentModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Set;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class PaymentRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    private static final MemberId ADMIN = MemberId.generate();
    private static final LocalDate PAID_ON = LocalDate.parse("2026-10-10");

    @Autowired
    private PaymentRepository payments;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private Subscription newSubscription(Association association) {
        return subscriptions.save(Fixtures.subscription(Fixtures.pack(association.id(), Set.of()), MemberId.generate(), "2026-10-01"));
    }

    private Payment pay(Subscription subscription, long cents) {
        return Payment.record(subscription, Money.ofCents(cents), PAID_ON, PaymentMethod.MB_WAY, ADMIN, CLOCK);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aPaymentAndItsReversalComeBackWithEveryFieldAndTheAudit() {
        // Arrange
        Association association = newAssociation();
        Subscription subscription = newSubscription(association);
        Payment payment = pay(subscription, 4500);
        Payment reversal = payment.reverse(MemberId.generate(), Fixtures.at(Fixtures.NOW.plusSeconds(60)));

        // Act
        payments.add(payment);
        payments.add(reversal);
        flushAndClear();

        // Assert
        assertThat(payments.findById(association.id(), payment.id())).hasValueSatisfying(loaded ->
                assertThat(loaded).usingRecursiveComparison().isEqualTo(payment));
        assertThat(payments.findById(association.id(), reversal.id())).hasValueSatisfying(loaded -> {
            assertThat(loaded).usingRecursiveComparison().isEqualTo(reversal);
            assertThat(loaded.reversalOf()).hasValue(payment.id());
            assertThat(loaded.signedCents()).isEqualTo(-4500);
        });
    }

    @Test
    void theSubscriptionsPaymentsComeBackOldestFirstAndOnlyThoseOfThatSubscription() {
        // Arrange
        Association association = newAssociation();
        Subscription subscription = newSubscription(association);
        Subscription another = newSubscription(association);
        Payment second = Payment.reconstruct(PaymentId.generate(), association.id(),
                subscription.id(), Money.ofCents(200), PAID_ON, PaymentMethod.CASH, ADMIN, Fixtures.NOW.plusSeconds(10), null);
        Payment first = pay(subscription, 100);
        payments.add(second);
        payments.add(first);
        payments.add(pay(another, 300));
        flushAndClear();

        // Act
        var found = payments.findBySubscription(association.id(), subscription.id());

        // Assert
        assertThat(found).extracting(Payment::id).containsExactly(first.id(), second.id());
    }

    @Test
    void aPaymentIsInvisibleToAnotherAssociationByIdAndBySubscription() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Subscription subscription = newSubscription(a);
        Payment payment = payments.add(pay(subscription, 4500));
        flushAndClear();

        // Act
        boolean visibleToB = payments.findById(b.id(), payment.id()).isPresent();
        var listedAsB = payments.findBySubscription(b.id(), subscription.id());

        // Assert
        assertThat(visibleToB).isFalse();
        assertThat(listedAsB).isEmpty();
        assertThat(payments.findBySubscription(a.id(), subscription.id())).hasSize(1);
    }

    @Test
    void thePortOffersNoWayToChangeOrRemoveAPayment() {
        // Arrange
        var methods = java.util.Arrays.stream(PaymentRepository.class.getMethods()).map(java.lang.reflect.Method::getName).toList();

        // Act
        // (the port's method names)

        // Assert
        assertThat(methods).containsExactlyInAnyOrder("findById", "findBySubscription", "add");
    }

    @Test
    void theDatabaseRefusesToUpdateAStoredPayment() {
        // Arrange
        Association association = newAssociation();
        Payment payment = payments.add(pay(newSubscription(association), 4500));
        flushAndClear();
        Executable update = () -> jdbc.update("update payments set amount_cents = 1 where id = ?", payment.id().value());

        // Act
        DataAccessException ex = assertThrows(DataAccessException.class, update);

        // Assert
        assertThat(ex.getMessage()).contains("append-only");
    }

    @Test
    void theDatabaseRefusesToDeleteAStoredPayment() {
        // Arrange
        Association association = newAssociation();
        Payment payment = payments.add(pay(newSubscription(association), 4500));
        flushAndClear();
        Executable delete = () -> jdbc.update("delete from payments where id = ?", payment.id().value());

        // Act
        DataAccessException ex = assertThrows(DataAccessException.class, delete);

        // Assert
        assertThat(ex.getMessage()).contains("append-only");
    }

    @Test
    void addingTheSamePaymentTwiceIsAConflictAndNeverOverwrites() {
        // Arrange
        Association association = newAssociation();
        Payment payment = payments.add(pay(newSubscription(association), 4500));
        flushAndClear();
        Payment sameIdOtherAmount = Payment.reconstruct(payment.id(), association.id(), payment.subscriptionId(),
                Money.ofCents(1), PAID_ON, PaymentMethod.CASH, ADMIN, Fixtures.NOW, null);
        Executable act = () -> payments.add(sameIdOtherAmount);

        // Act
        PaymentModifiedConcurrentlyException ex = assertThrows(PaymentModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.paymentId()).isEqualTo(payment.id());
    }

    @Test
    void aPaymentCannotBeReversedTwice() {
        // Arrange
        Association association = newAssociation();
        Payment payment = payments.add(pay(newSubscription(association), 4500));
        payments.add(payment.reverse(ADMIN, CLOCK));
        flushAndClear();
        Executable act = () -> payments.add(payment.reverse(ADMIN, CLOCK));

        // Act
        PaymentModifiedConcurrentlyException ex = assertThrows(PaymentModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.getCause()).isNotNull();
    }

    @Test
    void aReversalCannotPointAtAPaymentOfAnotherSubscription() {
        // Arrange
        Association association = newAssociation();
        Subscription subscription = newSubscription(association);
        Payment foreignPayment = payments.add(pay(newSubscription(association), 100));
        flushAndClear();
        Executable act = () -> payments.add(Payment.reconstruct(PaymentId.generate(), association.id(), subscription.id(),
                Money.ofCents(100), PAID_ON, PaymentMethod.CASH, ADMIN, Fixtures.NOW, foreignPayment.id()));

        // Act
        PaymentModifiedConcurrentlyException ex = assertThrows(PaymentModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.getCause()).isNotNull();
    }

    @Test
    void aViolationUnrelatedToConcurrencyIsNotDisguisedAsAConflict() {
        // Arrange
        Association association = newAssociation();
        Payment orphan = Payment.reconstruct(PaymentId.generate(), association.id(),
                com.regivolley.api.domain.model.valueobject.SubscriptionId.generate(), Money.ofCents(100), PAID_ON,
                PaymentMethod.CASH, ADMIN, Fixtures.NOW, null);
        Executable act = () -> payments.add(orphan);

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex).isNotInstanceOf(PaymentModifiedConcurrentlyException.class);
    }

    @Test
    void theDatabaseRefusesToReverseAReversal() {
        // Arrange
        Association association = newAssociation();
        Payment payment = payments.add(pay(newSubscription(association), 4500));
        Payment reversal = payments.add(payment.reverse(ADMIN, CLOCK));
        flushAndClear();
        Executable insert = () -> jdbc.update("""
                insert into payments (id, association_id, subscription_id, amount_cents, paid_on, method, recorded_by, recorded_at, reversal_of)
                values (?, ?, ?, 100, ?, 'CASH', ?, now(), ?)
                """, java.util.UUID.randomUUID(), association.id().value(), payment.subscriptionId().value(),
                java.sql.Date.valueOf(PAID_ON), ADMIN.value(), reversal.id().value());

        // Act
        DataAccessException ex = assertThrows(DataAccessException.class, insert);

        // Assert
        assertThat(ex.getMessage()).contains("reversal cannot be reversed");
    }

    @Test
    void theDatabaseRefusesToTruncateThePayments() {
        // Arrange
        Executable truncate = () -> jdbc.execute("truncate table payments");

        // Act
        DataAccessException ex = assertThrows(DataAccessException.class, truncate);

        // Assert
        assertThat(ex.getMessage()).contains("append-only");
    }
}
