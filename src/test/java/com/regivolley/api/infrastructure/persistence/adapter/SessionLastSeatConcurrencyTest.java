package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.NOW;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.POLICY;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.SESSION_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The last seat must never go to two people (architecture.md section 10, NFR "Concorrencia"). These
 * tests run real, separate, committed transactions against PostgreSQL: the default
 * rollback-per-test of the JPA slice would hide exactly the races under test. Each test uses its
 * own association, so leftovers do not interfere.
 */
@SpringBootTest
class SessionLastSeatConcurrencyTest extends AbstractPostgresIntegrationTest {

    private static final int ROUNDS = 25;

    @Autowired
    private SessionRepository sessions;
    @Autowired
    private AssociationRepository associations;

    private AssociationId newAssociation() {
        return associations.save(Fixtures.association()).id();
    }

    /**
     * Every contender first loads the session (all see the same version), waits at the gate, then
     * books its member and saves. Returns, per contender, the saved session or the failure.
     */
    private List<Outcome> race(Session stored, List<MemberId> contenders) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(contenders.size());
        try {
            CountDownLatch loaded = new CountDownLatch(contenders.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Session>> futures = new ArrayList<>();
            for (MemberId member : contenders) {
                Callable<Session> contender = () -> {
                    Session copy = sessions.findById(stored.associationId(), stored.id()).orElseThrow();
                    loaded.countDown();
                    go.await();
                    return sessions.save(copy.book(member, POLICY, Fixtures.at(NOW)).session());
                };
                futures.add(pool.submit(contender));
            }
            assertThat(loaded.await(30, TimeUnit.SECONDS)).as("every contender loaded the session").isTrue();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Session> future : futures) {
                outcomes.add(outcomeOf(future));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Outcome outcomeOf(Future<Session> future) throws Exception {
        try {
            return new Outcome(future.get(60, TimeUnit.SECONDS), null);
        } catch (ExecutionException e) {
            return new Outcome(null, e.getCause());
        }
    }

    private record Outcome(Session saved, Throwable failure) {
        boolean succeeded() {
            return saved != null;
        }
    }

    private static List<MemberId> members(int count) {
        return java.util.stream.Stream.generate(MemberId::generate).limit(count).toList();
    }

    @Test
    void twoMembersRacingForTheLastSeatExactlyOneWinsAndTheLoserGetsAConflict() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // Arrange
            AssociationId association = newAssociation();
            Session stored = sessions.save(Fixtures.session(association, SESSION_START, 1));
            List<MemberId> contenders = members(2);

            // Act
            List<Outcome> outcomes = race(stored, contenders);

            // Assert
            assertThat(outcomes).filteredOn(Outcome::succeeded).as("round %d winners", round).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                    .extracting(Outcome::failure).hasSize(1).allMatch(SessionModifiedConcurrentlyException.class::isInstance);
            Session finalState = sessions.findById(association, stored.id()).orElseThrow();
            assertThat(finalState.bookings()).hasSize(1);
            assertThat(finalState.bookings().get(0).status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(finalState.confirmedCount()).isEqualTo(1);
            assertThat(finalState.version()).isEqualTo(1L);
        }
    }

    @Test
    void manyMembersRacingForOneSeatStillOnlyOneIsConfirmed() throws Exception {
        // Arrange
        AssociationId association = newAssociation();
        Session stored = sessions.save(Fixtures.session(association, SESSION_START, 1));
        List<MemberId> contenders = members(8);

        // Act
        List<Outcome> outcomes = race(stored, contenders);

        // Assert
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).hasSize(7)
                .extracting(Outcome::failure).allMatch(SessionModifiedConcurrentlyException.class::isInstance);
        Session finalState = sessions.findById(association, stored.id()).orElseThrow();
        assertThat(finalState.confirmedCount()).isEqualTo(1);
        assertThat(finalState.bookings()).hasSize(1);
    }

    @Test
    void theLoserThatReloadsAndRetriesIsPlacedOnTheWaitlist() throws Exception {
        // Arrange
        AssociationId association = newAssociation();
        Session stored = sessions.save(Fixtures.session(association, SESSION_START, 1));
        MemberId first = MemberId.generate();
        MemberId second = MemberId.generate();
        List<Outcome> outcomes = race(stored, List.of(first, second));
        MemberId loser = outcomes.get(0).succeeded() ? second : first;

        // Act
        Session reloaded = sessions.findById(association, stored.id()).orElseThrow();
        sessions.save(reloaded.book(loser, POLICY, Fixtures.at(NOW)).session());

        // Assert
        Session finalState = sessions.findById(association, stored.id()).orElseThrow();
        assertThat(finalState.bookings()).extracting(Booking::status)
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED);
        assertThat(finalState.confirmedCount()).isEqualTo(1);
    }

    @Test
    void theSameMemberBookingTwiceAtOnceEndsWithOneLiveBookingAndTheRetryIsADuplicate() throws Exception {
        // Arrange
        AssociationId association = newAssociation();
        Session stored = sessions.save(Fixtures.session(association, SESSION_START, 12));
        MemberId member = MemberId.generate();

        // Act
        List<Outcome> outcomes = race(stored, List.of(member, member));
        Session reloaded = sessions.findById(association, stored.id()).orElseThrow();
        Executable retry = () -> reloaded.book(member, POLICY, Fixtures.at(NOW));

        // Assert
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::failure).allMatch(SessionModifiedConcurrentlyException.class::isInstance);
        assertThat(reloaded.bookings()).hasSize(1);
        assertThrows(DuplicateBookingException.class, retry);
    }

    @Test
    void bookingsOnDifferentSessionsRunningAtTheSameTimeDoNotConflict() throws Exception {
        for (int round = 0; round < 10; round++) {
            // Arrange
            AssociationId association = newAssociation();
            Session one = sessions.save(Fixtures.session(association, SESSION_START, 1));
            Session two = sessions.save(Fixtures.session(association, SESSION_START.plusSeconds(86_400), 1));
            Function<Session, Session> bookOne = s -> sessions.save(s.book(MemberId.generate(), POLICY, Fixtures.at(NOW)).session());
            Function<Session, Session> bookTwo = s -> sessions.save(s.book(MemberId.generate(), POLICY, Fixtures.at(NOW)).session());

            // Act
            List<Throwable> failures = Races.race(
                    List.<Callable<Session>>of(() -> sessions.findById(association, one.id()).orElseThrow(),
                            () -> sessions.findById(association, two.id()).orElseThrow()),
                    List.of(bookOne, bookTwo));

            // Assert
            assertThat(failures).as("round %d", round).isEmpty();
            assertThat(sessions.findById(association, one.id()).orElseThrow().confirmedCount()).isEqualTo(1);
            assertThat(sessions.findById(association, two.id()).orElseThrow().confirmedCount()).isEqualTo(1);
        }
    }

    @Test
    void aCancellationThatPromotesRacingACapacityChangeNeverDeadlocksOneWinsAndTheOtherGetsAConflict() throws Exception {
        for (int round = 0; round < 30; round++) {
            // Arrange - only booking rows change on one side, the session row and a booking on the other
            AssociationId association = newAssociation();
            MemberId seated = MemberId.generate();
            MemberId waiting = MemberId.generate();
            Session full = Fixtures.book(Fixtures.book(Fixtures.session(association, SESSION_START, 1), seated, 0), waiting, 1);
            Session stored = sessions.save(full);
            Booking seatedBooking = stored.bookings().stream().filter(b -> b.memberId().equals(seated)).findFirst().orElseThrow();
            Instant later = NOW.plusSeconds(100);
            Function<Session, Session> cancelAndPromote = s -> sessions.save(
                    s.cancelBooking(seatedBooking.id(), POLICY, Fixtures.at(later), member -> true).session());
            Function<Session, Session> raiseCapacity = s -> sessions.save(
                    s.changeCapacity(2, Fixtures.at(later), member -> true).session());

            // Act
            List<Throwable> failures = Races.race(
                    () -> sessions.findById(association, stored.id()).orElseThrow(),
                    List.of(cancelAndPromote, raiseCapacity));

            // Assert
            assertThat(failures).as("round %d: no deadlock, no raw persistence error", round).hasSize(1)
                    .allMatch(SessionModifiedConcurrentlyException.class::isInstance);
            Session finalState = sessions.findById(association, stored.id()).orElseThrow();
            assertThat(finalState.version()).isEqualTo(1L);
            // Whichever won, the stored state is consistent with it: the cancellation frees a seat that the
            // waiting member takes (1 confirmed), the capacity change seats the waiting member beside the first (2).
            assertThat(finalState.confirmedCount()).isEqualTo(finalState.capacity());
        }
    }
}
