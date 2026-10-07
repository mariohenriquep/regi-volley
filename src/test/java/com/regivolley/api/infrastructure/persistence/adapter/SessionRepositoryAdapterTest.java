package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.result.BookingCancellation;
import com.regivolley.api.domain.model.result.BookingResult;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.CLOCK;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.NOW;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.POLICY;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.SESSION_START;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.book;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class SessionRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SessionRepository sessions;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private AssociationId newAssociation() {
        return associations.save(Fixtures.association()).id();
    }

    /** Forces what was written to the database and forgets it, so the next read really hits PostgreSQL. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Session saveAndReload(Session session) {
        Session saved = sessions.save(session);
        flushAndClear();
        return sessions.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    private Long storedVersion(Session session) {
        return jdbc.queryForObject("select version from sessions where id = ?", Long.class, session.id().value());
    }

    @Nested
    class RoundTrip {

        @Test
        void aNewSessionComesBackWithAllItsFields() {
            // Arrange
            AssociationId association = newAssociation();
            Session session = session(association, SESSION_START, 12);

            // Act
            Session loaded = saveAndReload(session);

            // Assert
            assertThat(loaded).usingRecursiveComparison().isEqualTo(session);
            assertThat(loaded.version()).isZero();
        }

        @Test
        void bookingsOfEveryKindComeBackInArrivalOrder() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId a = MemberId.generate();
            MemberId b = MemberId.generate();
            MemberId c = MemberId.generate();
            MemberId d = MemberId.generate();
            Session session = book(book(book(session(association, SESSION_START, 2), a, 0), b, 1), c, 2);
            Instant promotedAt = NOW.plusSeconds(100);
            BookingCancellation cancellation = session.cancelBooking(
                    bookingOf(session, a).id(), POLICY, Fixtures.at(promotedAt), member -> true);
            Session expected = book(cancellation.session(), d, 3);

            // Act
            Session loaded = saveAndReload(expected);

            // Assert
            assertThat(loaded).usingRecursiveComparison().isEqualTo(expected);
            assertThat(loaded.bookings()).extracting(Booking::status).containsExactly(
                    BookingStatus.CANCELLED, BookingStatus.CONFIRMED, BookingStatus.CONFIRMED, BookingStatus.WAITLISTED);
            assertThat(bookingOf(loaded, c).confirmedAt()).contains(promotedAt);
            assertThat(bookingOf(loaded, d).confirmedAt()).isEmpty();
        }

        @Test
        void aCancelledSessionKeepsItsReasonAndItsBookingsCancelledBySession() {
            // Arrange
            AssociationId association = newAssociation();
            Session booked = book(session(association, SESSION_START, 12), MemberId.generate(), 0);
            Session cancelled = booked.cancel("Venue closed for maintenance");

            // Act
            Session loaded = saveAndReload(cancelled);

            // Assert
            assertThat(loaded).usingRecursiveComparison().isEqualTo(cancelled);
            assertThat(loaded.status()).isEqualTo(SessionStatus.CANCELLED);
            assertThat(loaded.cancellationReason()).contains("Venue closed for maintenance");
        }

        @Test
        void instantsAreStoredInUtc() {
            // Arrange
            AssociationId association = newAssociation();
            Instant start = Instant.parse("2026-10-20T19:30:00Z");
            Session session = session(association, start, 12);

            // Act
            sessions.save(session);
            flushAndClear();
            LocalDateTime storedUtc = jdbc.queryForObject(
                    "select starts_at at time zone 'UTC' from sessions where id = ?", LocalDateTime.class,
                    session.id().value());

            // Assert
            assertThat(storedUtc).isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 30));
        }

        @Test
        void aSessionIsUpdatedInPlaceNotDuplicated() {
            // Arrange
            AssociationId association = newAssociation();
            Session first = sessions.save(session(association, SESSION_START, 12));

            // Act
            Session second = sessions.save(first.changeCapacity(8, CLOCK, member -> true).session());
            flushAndClear();

            // Assert
            assertThat(jdbc.queryForObject("select count(*) from sessions where association_id = ?", Integer.class,
                    association.value())).isEqualTo(1);
            assertThat(sessions.findById(association, second.id()).orElseThrow().capacity()).isEqualTo(8);
        }
    }

    @Nested
    class Versioning {

        @Test
        void aNewSessionStartsAtVersionZero() {
            // Arrange
            Session session = session(newAssociation(), SESSION_START, 12);

            // Act
            Session saved = sessions.save(session);

            // Assert
            assertThat(saved.version()).isZero();
            assertThat(storedVersion(saved)).isZero();
        }

        @Test
        void savingOnlyANewBookingStillMovesTheVersionByOne() {
            // Arrange
            Session stored = sessions.save(session(newAssociation(), SESSION_START, 12));
            flushAndClear();
            Session loaded = sessions.findById(stored.associationId(), stored.id()).orElseThrow();
            Session withBooking = book(loaded, MemberId.generate(), 0);

            // Act
            Session saved = sessions.save(withBooking);
            flushAndClear();

            // Assert
            assertThat(saved.version()).isEqualTo(1L);
            assertThat(storedVersion(saved)).isEqualTo(1L);
            assertThat(saved.bookings()).hasSize(1);
        }

        @Test
        void savingAChangedColumnMovesTheVersionExactlyOnce() {
            // Arrange
            Session stored = sessions.save(session(newAssociation(), SESSION_START, 12));
            flushAndClear();
            Session loaded = sessions.findById(stored.associationId(), stored.id()).orElseThrow();

            // Act
            Session saved = sessions.save(loaded.changeCapacity(10, CLOCK, member -> true).session());
            flushAndClear();

            // Assert
            assertThat(saved.version()).isEqualTo(1L);
            assertThat(storedVersion(saved)).isEqualTo(1L);
        }

        @Test
        void everySaveInItsOwnTransactionMovesTheVersionOnEvenWithNothingChanged() {
            // Arrange - clearing the persistence context stands in for the end of each transaction
            Session first = sessions.save(session(newAssociation(), SESSION_START, 12));
            flushAndClear();

            // Act
            Session second = sessions.save(first);
            flushAndClear();
            Session third = sessions.save(second);
            flushAndClear();

            // Assert
            assertThat(second.version()).isEqualTo(1L);
            assertThat(third.version()).isEqualTo(2L);
            assertThat(storedVersion(third)).isEqualTo(2L);
        }

        @Test
        void aStaleCopyIsRejectedAndTheNewerStateStays() {
            // Arrange
            Session stored = sessions.save(session(newAssociation(), SESSION_START, 1));
            flushAndClear();
            Session copyA = sessions.findById(stored.associationId(), stored.id()).orElseThrow();
            Session copyB = sessions.findById(stored.associationId(), stored.id()).orElseThrow();
            MemberId winner = MemberId.generate();
            sessions.save(book(copyB, winner, 0));
            flushAndClear();
            Executable act = () -> sessions.save(book(copyA, MemberId.generate(), 0));

            // Act
            SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

            // Assert
            assertThat(ex.sessionId()).isEqualTo(stored.id());
            Session current = sessions.findById(stored.associationId(), stored.id()).orElseThrow();
            assertThat(current.bookings()).extracting(Booking::memberId).containsExactly(winner);
            assertThat(current.version()).isEqualTo(1L);
        }
    }

    @Nested
    class Queries {

        @Test
        void findStartingBetweenIsHalfOpenOrderedAndKeepsCancelledSessions() {
            // Arrange
            AssociationId association = newAssociation();
            Instant from = SESSION_START;
            Instant to = from.plus(Duration.ofDays(7));
            Session atFrom = sessions.save(session(association, from, 12));
            Session cancelled = sessions.save(session(association, from.plus(Duration.ofDays(1)), 12).cancel("Closed"));
            Session lateInWeek = sessions.save(session(association, to.minusSeconds(1), 12));
            sessions.save(session(association, to, 12));
            sessions.save(session(association, from.minusSeconds(1), 12));
            flushAndClear();

            // Act
            List<Session> week = sessions.findStartingBetween(association, from, to);

            // Assert
            assertThat(week).extracting(Session::id).containsExactly(atFrom.id(), cancelled.id(), lateInWeek.id());
            assertThat(week.get(1).status()).isEqualTo(SessionStatus.CANCELLED);
        }

        @Test
        void findStartingBetweenReturnsEverySessionWithItsBookings() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId a = MemberId.generate();
            MemberId b = MemberId.generate();
            sessions.save(book(book(session(association, SESSION_START, 12), a, 0), b, 1));
            flushAndClear();

            // Act
            List<Session> found = sessions.findStartingBetween(association, SESSION_START, SESSION_START.plusSeconds(1));

            // Assert
            assertThat(found).hasSize(1);
            assertThat(found.get(0).bookings()).extracting(Booking::memberId).containsExactly(a, b);
        }

        @Test
        void findByTrainingGroupReturnsOnlyThatGroupsSessionsCancelledOnesIncluded() {
            // Arrange
            AssociationId association = newAssociation();
            TrainingGroupId group = TrainingGroupId.generate();
            MemberId coach = MemberId.generate();
            Session live = sessions.save(session(association, group, coach, SESSION_START, 12));
            Session cancelled = sessions.save(
                    session(association, group, coach, SESSION_START.plus(Duration.ofDays(7)), 12).cancel("Closed"));
            sessions.save(session(association, TrainingGroupId.generate(), coach, SESSION_START, 12));
            sessions.save(session(association, group, coach, SESSION_START.plus(Duration.ofDays(14)), 12));
            flushAndClear();

            // Act
            List<Session> found = sessions.findByTrainingGroupStartingBetween(
                    association, group, SESSION_START, SESSION_START.plus(Duration.ofDays(14)));

            // Assert
            assertThat(found).extracting(Session::id).containsExactly(live.id(), cancelled.id());
        }

        @Test
        void findWithLiveBookingOverlappingKeepsOnlySessionsOverlappingWhereTheMemberHoldsALiveBooking() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId member = MemberId.generate();
            MemberId other = MemberId.generate();
            Instant from = SESSION_START;
            Instant to = from.plus(Duration.ofMinutes(90));
            Session overlapping = sessions.save(book(session(association, from.plusSeconds(1800), 12), member, 0));
            Session startsBeforeEndsInside = sessions.save(book(session(association, from.minusSeconds(3600), 12), member, 0));
            sessions.save(book(session(association, from.minus(Duration.ofMinutes(90)), 12), member, 0)); // ends exactly at from
            sessions.save(book(session(association, to, 12), member, 0));                                 // starts exactly at to
            sessions.save(book(session(association, from.plusSeconds(60), 12), other, 0));              // someone else's
            Session cancelledByMember = book(session(association, from.plusSeconds(120), 12), member, 0);
            Session afterCancel = cancelledByMember.cancelBooking(
                    bookingOf(cancelledByMember, member).id(), POLICY, CLOCK, m -> true).session();
            sessions.save(afterCancel);
            flushAndClear();

            // Act
            List<Session> found = sessions.findWithLiveBookingOverlapping(association, member, from, to);

            // Assert
            assertThat(found).extracting(Session::id).containsExactly(startsBeforeEndsInside.id(), overlapping.id());
        }

        @Test
        void aWaitlistedBookingCountsAsLiveForTheOverlapCheck() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId first = MemberId.generate();
            MemberId waiting = MemberId.generate();
            Session full = sessions.save(book(book(session(association, SESSION_START, 1), first, 0), waiting, 1));
            flushAndClear();

            // Act
            List<Session> found = sessions.findWithLiveBookingOverlapping(
                    association, waiting, SESSION_START, SESSION_START.plusSeconds(60));

            // Assert
            assertThat(found).extracting(Session::id).containsExactly(full.id());
        }

        @Test
        void theWaitlistIsReadInRequestTimeThenIdOrder() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId seated = MemberId.generate();
            List<MemberId> sameInstant = Stream.generate(MemberId::generate).limit(4).toList();
            MemberId earlier = MemberId.generate();
            Session session = book(session(association, SESSION_START, 1), seated, 0);
            session = book(session, earlier, 5);
            for (MemberId member : sameInstant) {
                session = book(session, member, 10);
            }
            Booking earliest = bookingOf(session, earlier);
            List<Booking> tied = session.bookings().stream()
                    .filter(booking -> sameInstant.contains(booking.memberId()))
                    .sorted(Comparator.comparing(booking -> booking.id().value().toString()))
                    .toList();

            // Act
            Session loaded = saveAndReload(session);

            // Assert
            List<Booking> expectedOrder = Stream.concat(Stream.of(earliest), tied.stream()).toList();
            assertThat(loaded.waitlist()).extracting(Booking::id)
                    .containsExactlyElementsOf(expectedOrder.stream().map(Booking::id).toList());
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void aSessionIsInvisibleToAnotherAssociationByIdAndInEveryList() {
            // Arrange
            AssociationId a = newAssociation();
            AssociationId b = newAssociation();
            MemberId member = MemberId.generate();
            TrainingGroupId group = TrainingGroupId.generate();
            Session ofA = sessions.save(book(session(a, group, MemberId.generate(), SESSION_START, 12), member, 0));
            Session ofB = sessions.save(session(b, SESSION_START, 12));
            flushAndClear();
            Instant to = SESSION_START.plus(Duration.ofDays(1));

            // Act
            boolean visibleByIdToB = sessions.findById(b, ofA.id()).isPresent();
            boolean visibleByIdToA = sessions.findById(a, ofB.id()).isPresent();
            List<Session> weekOfB = sessions.findStartingBetween(b, SESSION_START, to);
            List<Session> groupOfAAsB = sessions.findByTrainingGroupStartingBetween(b, group, SESSION_START, to);
            List<Session> overlapAsB = sessions.findWithLiveBookingOverlapping(b, member, SESSION_START, to);
            List<Session> weekOfA = sessions.findStartingBetween(a, SESSION_START, to);

            // Assert
            assertThat(visibleByIdToB).isFalse();
            assertThat(visibleByIdToA).isFalse();
            assertThat(weekOfB).extracting(Session::id).containsExactly(ofB.id());
            assertThat(groupOfAAsB).isEmpty();
            assertThat(overlapAsB).isEmpty();
            assertThat(weekOfA).extracting(Session::id).containsExactly(ofA.id());
            assertThat(sessions.findById(a, ofA.id())).isPresent();
        }

        @Test
        void sameStartAndGroupInTwoAssociationsAreIndependentSessions() {
            // Arrange
            AssociationId a = newAssociation();
            AssociationId b = newAssociation();
            TrainingGroupId sameGroupId = TrainingGroupId.generate();
            MemberId coach = MemberId.generate();

            // Act
            Session first = sessions.save(session(a, sameGroupId, coach, SESSION_START, 12));
            Session second = sessions.save(session(b, sameGroupId, coach, SESSION_START, 12));

            // Assert
            assertThat(first.id()).isNotEqualTo(second.id());
        }
    }

    @Nested
    class Constraints {

        @Test
        void theDatabaseRejectsASecondLiveBookingOfTheSameMemberInASession() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId member = MemberId.generate();
            Session session = sessions.save(book(session(association, SESSION_START, 12), member, 0));
            flushAndClear();
            Executable act = () -> jdbc.update("""
                    insert into bookings (id, association_id, session_id, member_id, status, requested_at, confirmed_at)
                    values (?, ?, ?, ?, 'CONFIRMED', ?::timestamptz, ?::timestamptz)""",
                    UUID.randomUUID(), association.value(), session.id().value(), member.value(),
                    NOW.toString(), NOW.toString());

            // Act
            DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("uq_bookings_live_member_session");
        }

        @Test
        void aMemberMayBookAgainAfterCancelling() {
            // Arrange
            AssociationId association = newAssociation();
            MemberId member = MemberId.generate();
            Session booked = book(session(association, SESSION_START, 12), member, 0);
            Session cancelled = booked.cancelBooking(bookingOf(booked, member).id(), POLICY, CLOCK, m -> true).session();
            BookingResult rebooked = cancelled.book(member, POLICY, Fixtures.at(NOW.plusSeconds(60)));

            // Act
            Session loaded = saveAndReload(rebooked.session());

            // Assert
            assertThat(loaded.bookings()).extracting(Booking::status)
                    .containsExactly(BookingStatus.CANCELLED, BookingStatus.CONFIRMED);
            assertThat(loaded.bookings()).extracting(Booking::memberId).containsOnly(member);
        }

        @Test
        void aSecondSessionForTheSameGroupAndStartIsRejected() {
            // Arrange
            AssociationId association = newAssociation();
            TrainingGroupId group = TrainingGroupId.generate();
            MemberId coach = MemberId.generate();
            sessions.save(session(association, group, coach, SESSION_START, 12));
            Executable act = () -> sessions.save(session(association, group, coach, SESSION_START, 12));

            // Act
            SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

            // Assert
            assertThat(ex.getCause()).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(ex.getCause().getMessage()).contains("uq_sessions_group_starts_at");
        }
    }

    private static Booking bookingOf(Session session, MemberId member) {
        return session.bookings().stream().filter(b -> b.memberId().equals(member)).findFirst().orElseThrow();
    }
}
