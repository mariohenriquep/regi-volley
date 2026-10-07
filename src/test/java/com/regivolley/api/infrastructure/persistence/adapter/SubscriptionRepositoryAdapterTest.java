package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.CreditUsage;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class SubscriptionRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant SESSION_1 = Instant.parse("2026-10-12T19:00:00Z");
    private static final Instant SESSION_2 = Instant.parse("2026-10-14T19:00:00Z");
    private static final Instant SESSION_3 = Instant.parse("2026-10-19T19:00:00Z");

    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Subscription saveAndReload(Subscription subscription) {
        Subscription saved = subscriptions.save(subscription);
        flushAndClear();
        return subscriptions.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    @Test
    void aPackSubscriptionWithUsagesRoundTripsInChargeOrder() {
        // Arrange
        Association association = newAssociation();
        Plan plan = Fixtures.pack(association.id(), Set.of(association.levels().get(1).id()));
        Subscription base = Fixtures.subscription(plan, MemberId.generate(), "2026-10-01");
        BookingId first = BookingId.generate();
        BookingId second = BookingId.generate();
        BookingId third = BookingId.generate();
        Subscription charged = base.consume(first, SESSION_1).consume(second, SESSION_2).consume(third, SESSION_3).markPaid();

        // Act
        Subscription loaded = saveAndReload(charged);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(charged);
        assertThat(loaded.usages()).extracting(CreditUsage::bookingId).containsExactly(first, second, third);
        assertThat(loaded.terms().allowedLevels()).containsExactly(association.levels().get(1).id());
        assertThat(loaded.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(loaded.endDate()).isEqualTo(LocalDate.parse("2026-12-29"));
    }

    @Test
    void aWeeklySubscriptionKeepsItsTermsSnapshot() {
        // Arrange
        Association association = newAssociation();
        Plan plan = Fixtures.monthlyNPerWeek(association.id());
        Subscription subscription = Fixtures.subscription(plan, MemberId.generate(), "2026-10-01");

        // Act
        Subscription loaded = saveAndReload(subscription);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(subscription);
        assertThat(loaded.terms().sessionsPerWeek()).isEqualTo(2);
        assertThat(loaded.terms().credits()).isNull();
    }

    @Test
    void refundingAMiddleUsageKeepsTheOthersInOrder() {
        // Arrange
        Association association = newAssociation();
        Plan plan = Fixtures.pack(association.id(), Set.of());
        BookingId first = BookingId.generate();
        BookingId second = BookingId.generate();
        BookingId third = BookingId.generate();
        Subscription charged = Fixtures.subscription(plan, MemberId.generate(), "2026-10-01")
                .consume(first, SESSION_1).consume(second, SESSION_2).consume(third, SESSION_3);
        subscriptions.save(charged);
        flushAndClear();

        // Act
        Subscription loaded = saveAndReload(charged.refund(second));

        // Assert
        assertThat(loaded.usages()).extracting(CreditUsage::bookingId).containsExactly(first, third);
        assertThat(loaded.holdsPlaceFor(second)).isFalse();
    }

    @Test
    void paymentStatusChangesAreUpdatedInPlace() {
        // Arrange
        Association association = newAssociation();
        Subscription pending = subscriptions.save(
                Fixtures.subscription(Fixtures.pack(association.id(), Set.of()), MemberId.generate(), "2026-10-01"));
        flushAndClear();

        // Act
        Subscription loaded = saveAndReload(pending.markOverdue());

        // Assert
        assertThat(loaded.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    void aNewSubscriptionStartsAtVersionZeroAndEverySaveMovesItByExactlyOne() {
        // Arrange
        Association association = newAssociation();
        Subscription pending = subscriptions.save(
                Fixtures.subscription(Fixtures.pack(association.id(), Set.of()), MemberId.generate(), "2026-10-01"));
        flushAndClear();

        // Act
        Subscription charged = subscriptions.save(
                subscriptions.findById(association.id(), pending.id()).orElseThrow()
                        .consume(BookingId.generate(), SESSION_1));
        flushAndClear();
        Subscription paid = subscriptions.save(charged.markPaid());
        flushAndClear();

        // Assert
        assertThat(pending.version()).isZero();
        assertThat(charged.version()).isEqualTo(1L);
        assertThat(paid.version()).isEqualTo(2L);
        assertThat(subscriptions.findById(association.id(), pending.id()).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void aStaleCopyIsRejectedAndTheNewerChargeStays() {
        // Arrange
        Association association = newAssociation();
        Subscription stored = subscriptions.save(
                Fixtures.subscription(Fixtures.pack(association.id(), Set.of()), MemberId.generate(), "2026-10-01"));
        flushAndClear();
        Subscription copyA = subscriptions.findById(association.id(), stored.id()).orElseThrow();
        Subscription copyB = subscriptions.findById(association.id(), stored.id()).orElseThrow();
        BookingId winner = BookingId.generate();
        subscriptions.save(copyB.consume(winner, SESSION_1));
        flushAndClear();
        Executable act = () -> subscriptions.save(copyA.consume(BookingId.generate(), SESSION_2));

        // Act
        SubscriptionModifiedConcurrentlyException ex = assertThrows(SubscriptionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.subscriptionId()).isEqualTo(stored.id());
        assertThat(subscriptions.findById(association.id(), stored.id()).orElseThrow().usages())
                .extracting(CreditUsage::bookingId).containsExactly(winner);
    }

    @Test
    void findByMemberReturnsOnlyThatMembersSubscriptionsOldestFirst() {
        // Arrange
        Association association = newAssociation();
        MemberId member = MemberId.generate();
        Plan plan = Fixtures.monthlyNPerWeek(association.id());
        Subscription later = subscriptions.save(Fixtures.subscription(plan, member, "2026-12-01"));
        Subscription earlier = subscriptions.save(Fixtures.subscription(plan, member, "2026-10-01"));
        subscriptions.save(Fixtures.subscription(plan, MemberId.generate(), "2026-10-01"));
        flushAndClear();

        // Act
        List<Subscription> found = subscriptions.findByMember(association.id(), member);

        // Assert
        assertThat(found).extracting(Subscription::id).containsExactly(earlier.id(), later.id());
    }

    @Test
    void subscriptionsAreInvisibleToAnotherAssociationByIdAndByMember() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        MemberId member = MemberId.generate();
        Subscription ofA = subscriptions.save(Fixtures.subscription(Fixtures.pack(a.id(), Set.of()), member, "2026-10-01"));
        flushAndClear();

        // Act
        boolean visibleToB = subscriptions.findById(b.id(), ofA.id()).isPresent();
        List<Subscription> memberAsB = subscriptions.findByMember(b.id(), member);

        // Assert
        assertThat(visibleToB).isFalse();
        assertThat(memberAsB).isEmpty();
        assertThat(subscriptions.findByMember(a.id(), member)).extracting(Subscription::id).containsExactly(ofA.id());
    }
}
