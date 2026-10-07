package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * One member booking two different sessions at the same moment must not spend the last credit of
 * a pack twice (architecture.md section 10). The session lock does not help here: the two bookings
 * are in different sessions, so the subscription carries its own version. Real, separate,
 * committed transactions.
 */
@SpringBootTest
class SubscriptionConcurrencyTest extends AbstractPostgresIntegrationTest {

    private static final Instant SESSION_1 = Instant.parse("2026-10-12T19:00:00Z");
    private static final Instant SESSION_2 = Instant.parse("2026-10-14T19:00:00Z");

    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private AssociationRepository associations;

    private Subscription lastCreditPack(Association association, MemberId member) {
        Plan onlyOne = Plan.create(association.id(), "One credit", PlanTerms.pack(1, Set.of()), Money.ofCents(500), 90);
        return subscriptions.save(Fixtures.subscription(onlyOne, member, "2026-10-01"));
    }

    @Test
    void twoBookingsSpendingTheLastCreditOfAPackAtOnceExactlyOneIsStored() throws Exception {
        for (int round = 0; round < 15; round++) {
            // Arrange
            Association association = associations.save(Fixtures.association());
            MemberId member = MemberId.generate();
            Subscription stored = lastCreditPack(association, member);
            Function<Subscription, Subscription> bookSession1 = s -> subscriptions.save(s.consume(BookingId.generate(), SESSION_1));
            Function<Subscription, Subscription> bookSession2 = s -> subscriptions.save(s.consume(BookingId.generate(), SESSION_2));

            // Act
            List<Throwable> failures = Races.race(
                    () -> subscriptions.findById(association.id(), stored.id()).orElseThrow(),
                    List.of(bookSession1, bookSession2));

            // Assert
            assertThat(failures).as("round %d", round).hasSize(1)
                    .allMatch(SubscriptionModifiedConcurrentlyException.class::isInstance);
            Subscription finalState = subscriptions.findById(association.id(), stored.id()).orElseThrow();
            assertThat(finalState.usages()).hasSize(1);
            assertThat(finalState.version()).isEqualTo(1L);
        }
    }

    @Test
    void theLoserThatReloadsFindsNoBalanceLeft() throws Exception {
        // Arrange
        Association association = associations.save(Fixtures.association());
        Subscription stored = lastCreditPack(association, MemberId.generate());
        Function<Subscription, Subscription> book1 = s -> subscriptions.save(s.consume(BookingId.generate(), SESSION_1));
        Function<Subscription, Subscription> book2 = s -> subscriptions.save(s.consume(BookingId.generate(), SESSION_2));
        Races.race(() -> subscriptions.findById(association.id(), stored.id()).orElseThrow(), List.of(book1, book2));
        Subscription reloaded = subscriptions.findById(association.id(), stored.id()).orElseThrow();
        Executable retry = () -> reloaded.consume(BookingId.generate(), SESSION_2);

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, retry);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_BALANCE);
    }
}
