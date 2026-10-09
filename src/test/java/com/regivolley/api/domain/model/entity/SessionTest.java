package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.AttendanceAlreadyMarkedException;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.BookingOverlapException;
import com.regivolley.api.domain.exception.BookingWindowClosedException;
import com.regivolley.api.domain.exception.CancellationClosedException;
import com.regivolley.api.domain.exception.CancellationReasonRequiredException;
import com.regivolley.api.domain.exception.CapacityBelowConfirmedException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.InvalidBookingStatusTransitionException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.InvalidSessionStatusTransitionException;
import com.regivolley.api.domain.exception.SessionAlreadyStartedException;
import com.regivolley.api.domain.exception.SessionNotScheduledException;
import com.regivolley.api.domain.exception.SessionNotStartedException;
import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.model.result.BookingCancellation;
import com.regivolley.api.domain.model.result.BookingResult;
import com.regivolley.api.domain.model.result.CapacityChange;
import com.regivolley.api.domain.model.result.WaitlistPromotion;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTest {
    // 20:00 Lisbon (WEST, UTC+1) on Tuesday 20 Oct 2026; DST ends on 25 Oct, so the window opens
    // at the same wall-clock time on 13 Oct.
    private static final Instant START = Instant.parse("2026-10-20T19:00:00Z");
    private static final Instant END = START.plus(Duration.ofMinutes(90));
    private static final Instant OPENS_AT = Instant.parse("2026-10-13T19:00:00Z");
    private static final Instant FREE_CANCELLATION_DEADLINE = Instant.parse("2026-10-20T13:00:00Z");

    private static final AssociationId ASSOCIATION = AssociationId.generate();
    private static final TrainingGroupId GROUP = TrainingGroupId.generate();
    private static final MemberId COACH = MemberId.generate();
    private static final BookingPolicy POLICY = BookingPolicy.defaults();
    private static final Predicate<MemberId> EVERYONE = member -> true;
    private static final Clock PROMOTION_CLOCK = Clock.fixed(OPENS_AT.plusSeconds(500), ZoneOffset.UTC);

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static Session newSession(int capacity) {
        return SessionFactory.create(ASSOCIATION, GROUP, COACH, START, END, capacity);
    }

    private static Session sessionIn(SessionStatus status) {
        String reason = status == SessionStatus.CANCELLED ? "Venue unavailable" : null;
        return SessionFactory.reconstitute(SessionId.generate(), ASSOCIATION, GROUP, COACH, START, END, 12, status,
                reason, List.of(), 0L);
    }

    /** Books the member one second after the window opens plus {@code offsetSeconds}, so arrival order is explicit. */
    private static Session bookAt(Session session, MemberId member, long offsetSeconds) {
        return session.book(member, POLICY, at(OPENS_AT.plusSeconds(offsetSeconds))).session();
    }

    /** A session with {@code members.size()} bookings made in list order (first ones fill the seats). */
    private static Session bookedBy(int capacity, List<MemberId> members) {
        Session session = newSession(capacity);
        for (int i = 0; i < members.size(); i++) {
            session = bookAt(session, members.get(i), i);
        }
        return session;
    }

    private static List<MemberId> members(int count) {
        return Stream.generate(MemberId::generate).limit(count).toList();
    }

    private static Booking bookingOf(Session session, MemberId member) {
        return session.bookings().stream().filter(b -> b.memberId().equals(member)).findFirst().orElseThrow();
    }

    /** A booking as {@code Session.book} makes it: CONFIRMED is confirmed at the request instant, WAITLISTED never was. */
    private static Booking bookingOf(AssociationId association, SessionId session, MemberId member, BookingStatus status,
                                     Instant requestedAt) {
        Instant confirmedAt = status == BookingStatus.CONFIRMED ? requestedAt : null;
        return SessionFactory.reconstituteBooking(BookingId.generate(), association, session, member, status, requestedAt,
                confirmedAt, null);
    }

    @Nested
    class Creation {
        @Test
        void operationsCarryTheVersionUnchanged() {
            // Arrange
            List<MemberId> people = members(1);
            SessionId sessionId = SessionId.generate();
            Session session = SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END, 12,
                    SessionStatus.SCHEDULED, null, List.of(), 41L);

            // Act
            Session booked = session.book(people.get(0), POLICY, at(OPENS_AT)).session();
            Session resized = booked.changeCapacity(10, PROMOTION_CLOCK, EVERYONE).session();
            Session completed = resized.complete(at(START));

            // Assert
            assertThat(booked.version()).isEqualTo(41L);
            assertThat(resized.version()).isEqualTo(41L);
            assertThat(completed.version()).isEqualTo(41L);
            assertThat(session.cancel("Reason").version()).isEqualTo(41L);
        }

        @Test
        void equalsAndHashCodeUseTheId() {
            // Arrange
            Session session = newSession(12);
            Session sameIdOtherState = session.cancel("No venue");
            Session other = newSession(12);

            // Act
            boolean sameId = session.equals(sameIdOtherState);
            boolean differentId = session.equals(other);

            // Assert
            assertThat(sameId).isTrue();
            assertThat(session).hasSameHashCodeAs(sameIdOtherState);
            assertThat(differentId).isFalse();
            assertThat(session).isNotEqualTo("not a session");
            assertThat(session).isEqualTo(session);
            assertThat(session.toString()).contains("SCHEDULED");
        }
    }

    @Nested
    class Status {
        private static final SessionStatus[] TARGETS = {SessionStatus.COMPLETED, SessionStatus.CANCELLED};

        private static Stream<Arguments> transitions(boolean legal) {
            Stream.Builder<Arguments> all = Stream.builder();
            for (SessionStatus from : SessionStatus.values()) {
                for (SessionStatus to : TARGETS) {
                    if ((from == SessionStatus.SCHEDULED) == legal) {
                        all.add(Arguments.of(from, to));
                    }
                }
            }
            return all.build();
        }

        static Stream<Arguments> legalTransitions() {
            return transitions(true);
        }

        static Stream<Arguments> illegalTransitions() {
            return transitions(false);
        }

        private Session transition(Session session, SessionStatus target) {
            Clock afterStart = at(START.plusSeconds(1));
            return target == SessionStatus.COMPLETED ? session.complete(afterStart) : session.cancel("Reason");
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("legalTransitions")
        void allowsTheLegalRn05Transitions(SessionStatus from, SessionStatus to) {
            // Arrange
            Session session = sessionIn(from);

            // Act
            Session moved = transition(session, to);

            // Assert
            assertThat(moved.status()).isEqualTo(to);
            assertThat(session.status()).isEqualTo(from);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("illegalTransitions")
        void rejectsTheIllegalRn05Transitions(SessionStatus from, SessionStatus to) {
            // Arrange
            Session session = sessionIn(from);
            Executable act = () -> transition(session, to);

            // Act
            InvalidSessionStatusTransitionException ex =
                    assertThrows(InvalidSessionStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(from);
            assertThat(ex.to()).isEqualTo(to);
            assertThat(ex.getMessage()).startsWith("Cannot move the session");
        }
    }

    @Nested
    class BookingRequests {
        @Test
        void confirmsTheBookingWhenASeatIsFree() {
            // Arrange
            Session session = newSession(2);
            MemberId member = MemberId.generate();

            // Act
            BookingResult result = session.book(member, POLICY, at(OPENS_AT.plusSeconds(5)));

            // Assert
            Booking booking = result.booking();
            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.memberId()).isEqualTo(member);
            assertThat(booking.sessionId()).isEqualTo(session.id());
            assertThat(booking.associationId()).isEqualTo(ASSOCIATION);
            assertThat(booking.requestedAt()).isEqualTo(OPENS_AT.plusSeconds(5));
            assertThat(result.session().bookings()).containsExactly(booking);
            assertThat(result.session().confirmedCount()).isEqualTo(1);
            assertThat(result.session().freeSeats()).isEqualTo(1);
        }

        @Test
        void doesNotMutateTheOriginalSession() {
            // Arrange
            Session session = newSession(2);

            // Act
            session.book(MemberId.generate(), POLICY, at(OPENS_AT));

            // Assert
            assertThat(session.bookings()).isEmpty();
        }

        @Test
        void lastSeatIsConfirmedAndTheNextRequestIsWaitlisted() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(2, people.subList(0, 1));

            // Act
            Session full = bookAt(session, people.get(1), 10);
            Session withWaitlist = bookAt(full, people.get(2), 20);

            // Assert
            assertThat(bookingOf(full, people.get(1)).status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(full.freeSeats()).isZero();
            assertThat(bookingOf(withWaitlist, people.get(2)).status()).isEqualTo(BookingStatus.WAITLISTED);
            assertThat(withWaitlist.confirmedCount()).isEqualTo(2);
            assertThat(withWaitlist.freeSeats()).isZero();
        }

        @Test
        void waitlistIsOrderedByArrivalAndExposesPositions() {
            // Arrange
            List<MemberId> people = members(5);
            Session session = bookedBy(2, people);

            // Act
            List<Booking> waitlist = session.waitlist();

            // Assert
            assertThat(waitlist).extracting(Booking::memberId).containsExactly(people.get(2), people.get(3), people.get(4));
            assertThat(session.waitlistPosition(people.get(2))).hasValue(1);
            assertThat(session.waitlistPosition(people.get(4))).hasValue(3);
            assertThat(session.waitlistPosition(people.get(0))).isEmpty();
            assertThat(session.waitlistPosition(MemberId.generate())).isEmpty();
        }

        @Test
        void waitlistOrdersByRequestTimeEvenIfReconstructedOutOfOrder() {
            // Arrange
            SessionId sessionId = SessionId.generate();
            MemberId late = MemberId.generate();
            MemberId early = MemberId.generate();
            Booking lateBooking = bookingOf(ASSOCIATION, sessionId, late, BookingStatus.WAITLISTED, OPENS_AT.plusSeconds(9));
            Booking earlyBooking = bookingOf(ASSOCIATION, sessionId, early, BookingStatus.WAITLISTED, OPENS_AT.plusSeconds(1));
            Session session = SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END, 1,
                    SessionStatus.SCHEDULED, null, List.of(lateBooking, earlyBooking), 0L);

            // Act
            List<Booking> waitlist = session.waitlist();

            // Assert
            assertThat(waitlist).extracting(Booking::memberId).containsExactly(early, late);
        }

        @Test
        void rejectsTheSessionCoachWithoutTakingASeat() {
            // Arrange
            Session session = newSession(2);
            Executable act = () -> session.book(COACH, POLICY, at(OPENS_AT));

            // Act
            CoachCannotBookOwnSessionException ex = assertThrows(CoachCannotBookOwnSessionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("coach");
            assertThat(session.bookings()).isEmpty();
            assertThat(session.freeSeats()).isEqualTo(2);
        }

        @Test
        void aMemberWhoIsCoachOfAnotherSessionCanBook() {
            // Arrange
            Session session = newSession(2);
            MemberId otherCoach = MemberId.generate();

            // Act
            BookingResult result = session.book(otherCoach, POLICY, at(OPENS_AT));

            // Assert
            assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void rejectsADuplicateConfirmedBooking() {
            // Arrange
            MemberId member = MemberId.generate();
            Session session = bookedBy(2, List.of(member));
            Executable act = () -> session.book(member, POLICY, at(OPENS_AT.plusSeconds(60)));

            // Act
            assertThrows(DuplicateBookingException.class, act);

            // Assert
            assertThat(session.bookings()).hasSize(1);
        }

        @Test
        void rejectsADuplicateWaitlistedBooking() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(1, people);
            Executable act = () -> session.book(people.get(1), POLICY, at(OPENS_AT.plusSeconds(60)));

            // Act
            assertThrows(DuplicateBookingException.class, act);

            // Assert
            assertThat(session.waitlist()).hasSize(1);
        }

        @Test
        void allowsABookingAgainAfterCancelling() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId first = bookingOf(session, people.get(0)).id();
            Session cancelled = session.cancelBooking(first, POLICY, at(OPENS_AT.plusSeconds(30)), EVERYONE).session();

            // Act
            BookingResult again = cancelled.book(people.get(0), POLICY, at(OPENS_AT.plusSeconds(60)));

            // Assert
            assertThat(again.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(again.booking().id()).isNotEqualTo(first);
            assertThat(again.session().bookings()).hasSize(2);
        }

        @Test
        void aMemberWhoCancelledAndRebooksJoinsTheBackOfTheQueue() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            MemberId returning = people.get(1);
            Session cancelled = session.cancelBooking(bookingOf(session, returning).id(), POLICY,
                    at(OPENS_AT.plusSeconds(30)), member -> false).session();

            // Act
            BookingResult again = cancelled.book(returning, POLICY, at(OPENS_AT.plusSeconds(60)));

            // Assert
            assertThat(again.booking().requestedAt()).isEqualTo(OPENS_AT.plusSeconds(60));
            assertThat(again.session().waitlist()).extracting(Booking::memberId)
                    .containsExactly(people.get(2), returning);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void attendedAndNoShowBookingsStillBlockBookingAgain(boolean attended) {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Session marked = attended ? session.markAttended(id, at(START)) : session.markNoShow(id, at(START));
            Executable act = () -> marked.book(people.get(0), POLICY, at(START.minusSeconds(1)));

            // Act
            assertThrows(DuplicateBookingException.class, act);

            // Assert
            assertThat(marked.bookings()).hasSize(1);
        }

        @Test
        void aNewRequestTakesAFreeSeatAheadOfAnAllIneligibleWaitlist() {
            // Arrange
            List<MemberId> people = members(3);
            Session full = bookedBy(2, people);
            Session freed = full.cancelBooking(bookingOf(full, people.get(0)).id(), POLICY,
                    at(OPENS_AT.plusSeconds(100)), member -> false).session();
            MemberId newcomer = MemberId.generate();

            // Act
            BookingResult result = freed.book(newcomer, POLICY, at(OPENS_AT.plusSeconds(200)));

            // Assert
            assertThat(freed.freeSeats()).isEqualTo(1);
            assertThat(freed.waitlist()).extracting(Booking::memberId).containsExactly(people.get(2));
            assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(result.session().waitlist()).extracting(Booking::memberId).containsExactly(people.get(2));
        }

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
        void rejectsBookingASessionThatIsNoLongerScheduled(SessionStatus status) {
            // Arrange
            Session session = sessionIn(status);
            Executable act = () -> session.book(MemberId.generate(), POLICY, at(OPENS_AT));

            // Act
            SessionNotScheduledException ex = assertThrows(SessionNotScheduledException.class, act);

            // Assert
            assertThat(ex.status()).isEqualTo(status);
        }
    }

    @Nested
    class BookingWindow {
        @Test
        void acceptsABookingExactlyWhenTheWindowOpens() {
            // Arrange
            Session session = newSession(2);

            // Act
            BookingResult result = session.book(MemberId.generate(), POLICY, at(OPENS_AT));

            // Assert
            assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void rejectsABookingOneSecondBeforeTheWindowOpens() {
            // Arrange
            Session session = newSession(2);
            Executable act = () -> session.book(MemberId.generate(), POLICY, at(OPENS_AT.minusSeconds(1)));

            // Act
            BookingWindowClosedException ex = assertThrows(BookingWindowClosedException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("opens at 13/10/2026 20:00");
        }

        @Test
        void acceptsABookingOneSecondBeforeTheStart() {
            // Arrange
            Session session = newSession(2);

            // Act
            BookingResult result = session.book(MemberId.generate(), POLICY, at(START.minusSeconds(1)));

            // Assert
            assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void rejectsABookingExactlyAtTheStart() {
            // Arrange
            Session session = newSession(2);
            Executable act = () -> session.book(MemberId.generate(), POLICY, at(START));

            // Act
            BookingWindowClosedException ex = assertThrows(BookingWindowClosedException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("closed at 20/10/2026 20:00");
        }

        @Test
        void rejectsABookingAfterTheStart() {
            // Arrange
            Session session = newSession(2);
            Executable act = () -> session.book(MemberId.generate(), POLICY, at(START.plusSeconds(1)));

            // Act
            assertThrows(BookingWindowClosedException.class, act);

            // Assert
            assertThat(session.bookings()).isEmpty();
        }

        @Test
        void honoursAConfiguredWindow() {
            // Arrange
            Session session = newSession(2);
            BookingPolicy threeDays = new BookingPolicy(3, 6);
            Instant opens = Instant.parse("2026-10-17T19:00:00Z");
            Executable tooEarly = () -> session.book(MemberId.generate(), threeDays, at(opens.minusSeconds(1)));

            // Act
            assertThrows(BookingWindowClosedException.class, tooEarly);
            BookingResult onTime = session.book(MemberId.generate(), threeDays, at(opens));

            // Assert
            assertThat(onTime.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void opensAtTheSameLisbonWallClockTimeAcrossTheDstChange() {
            // Arrange - class at 20:00 Lisbon on 29 Mar 2026 (19:00Z, WEST); a week before Lisbon was on WET (UTC+0)
            Instant start = Instant.parse("2026-03-29T19:00:00Z");
            Session session = SessionFactory.create(ASSOCIATION, GROUP, COACH, start, start.plus(Duration.ofMinutes(90)), 12);
            Instant opens = Instant.parse("2026-03-22T20:00:00Z");
            Executable oneSecondEarly = () -> session.book(MemberId.generate(), POLICY, at(opens.minusSeconds(1)));

            // Act
            assertThrows(BookingWindowClosedException.class, oneSecondEarly);
            BookingResult onTime = session.book(MemberId.generate(), POLICY, at(opens));

            // Assert
            assertThat(onTime.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        }
    }

    @Nested
    class CancelBooking {
        @Test
        void cancelsFreelyWellBeforeTheDeadline() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBooking(id, POLICY, at(START.minus(Duration.ofDays(2))), EVERYONE);

            // Assert
            assertThat(result.isLate()).isFalse();
            assertThat(result.cancelled().status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.FREE);
            assertThat(result.session().freeSeats()).isEqualTo(2);
            assertThat(result.promoted()).isEmpty();
        }

        @Test
        void cancellationExactlyAtTheDeadlineIsStillFree() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBooking(id, POLICY, at(FREE_CANCELLATION_DEADLINE), EVERYONE);

            // Assert
            assertThat(result.isLate()).isFalse();
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.FREE);
        }

        @Test
        void cancellationOneSecondAfterTheDeadlineIsLateButFreesTheSeat() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result =
                    session.cancelBooking(id, POLICY, at(FREE_CANCELLATION_DEADLINE.plusSeconds(1)), EVERYONE);

            // Assert
            assertThat(result.isLate()).isTrue();
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.LATE);
            assertThat(result.session().freeSeats()).isEqualTo(2);
        }

        @Test
        void honoursAConfiguredDeadline() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            BookingPolicy twelveHours = new BookingPolicy(7, 12);
            Instant deadline = START.minus(Duration.ofHours(12));

            // Act
            BookingCancellation onTime = session.cancelBooking(id, twelveHours, at(deadline), EVERYONE);
            BookingCancellation late = session.cancelBooking(id, twelveHours, at(deadline.plusSeconds(1)), EVERYONE);

            // Assert
            assertThat(onTime.isLate()).isFalse();
            assertThat(late.isLate()).isTrue();
        }

        @Test
        void aLateCancellationStillPromotesTheFirstWaitlistedBooking() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result =
                    session.cancelBooking(id, POLICY, at(FREE_CANCELLATION_DEADLINE.plusSeconds(1)), EVERYONE);

            // Assert
            assertThat(result.isLate()).isTrue();
            assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(people.get(2));
            assertThat(bookingOf(result.session(), people.get(2)).status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(result.session().waitlist()).isEmpty();
        }

        @Test
        void promotesOnlyTheFirstInArrivalOrderIntoASingleFreedSeat() {
            // Arrange
            List<MemberId> people = members(5);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(1)).id();

            // Act
            BookingCancellation result = session.cancelBooking(id, POLICY, at(OPENS_AT.plusSeconds(100)), EVERYONE);

            // Assert
            assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(people.get(2));
            assertThat(result.session().waitlist()).extracting(Booking::memberId)
                    .containsExactly(people.get(3), people.get(4));
            assertThat(result.session().freeSeats()).isZero();
        }

        @Test
        void skipsIneligibleWaitlistedMembersInOrderAndKeepsThemWaitlisted() {
            // Arrange
            List<MemberId> people = members(5);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Predicate<MemberId> noBalance = member -> !member.equals(people.get(2));

            // Act
            BookingCancellation result = session.cancelBooking(id, POLICY, at(OPENS_AT.plusSeconds(100)), noBalance);

            // Assert
            assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(people.get(3));
            assertThat(result.session().waitlist()).extracting(Booking::memberId)
                    .containsExactly(people.get(2), people.get(4));
            assertThat(result.session().waitlistPosition(people.get(2))).hasValue(1);
        }

        @Test
        void promotesNobodyWhenEveryWaitlistedMemberIsIneligible() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBooking(id, POLICY, at(OPENS_AT.plusSeconds(100)), member -> false);

            // Assert
            assertThat(result.promoted()).isEmpty();
            assertThat(result.session().freeSeats()).isEqualTo(1);
            assertThat(result.session().waitlist()).hasSize(2);
        }

        @Test
        void cancellingAWaitlistedBookingIsFreeEvenAfterTheDeadlineAndPromotesNobody() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            BookingId waitlisted = bookingOf(session, people.get(1)).id();

            // Act
            BookingCancellation result =
                    session.cancelBooking(waitlisted, POLICY, at(FREE_CANCELLATION_DEADLINE.plusSeconds(1)), EVERYONE);

            // Assert
            assertThat(result.isLate()).isFalse();
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.FREE);
            assertThat(result.promoted()).isEmpty();
            assertThat(result.session().waitlist()).extracting(Booking::memberId).containsExactly(people.get(2));
            assertThat(result.session().confirmedCount()).isEqualTo(1);
        }

        @Test
        void cancellationOneSecondBeforeTheStartIsAllowedAndLate() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBooking(id, POLICY, at(START.minusSeconds(1)), EVERYONE);

            // Assert
            assertThat(result.isLate()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 1, 3600})
        void rejectsCancellingAtOrAfterTheStart(long secondsAfterStart) {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(1, people);
            BookingId confirmed = bookingOf(session, people.get(0)).id();
            Executable act = () -> session.cancelBooking(confirmed, POLICY, at(START.plusSeconds(secondsAfterStart)), EVERYONE);

            // Act
            CancellationClosedException ex = assertThrows(CancellationClosedException.class, act);

            // Assert
            assertThat(ex.startsAt()).isEqualTo(START);
            assertThat(ex.getMessage()).contains("20/10/2026 20:00");
            assertThat(bookingOf(session, people.get(0)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void aPromotedBookingCancelledAfterTheDeadlineIsLate() {
            // Arrange
            List<MemberId> people = members(3);
            Session full = bookedBy(2, people);
            Session promoted = full.cancelBooking(bookingOf(full, people.get(0)).id(), POLICY,
                    at(FREE_CANCELLATION_DEADLINE.plusSeconds(1)), EVERYONE).session();
            BookingId promotedId = bookingOf(promoted, people.get(2)).id();

            // Act
            BookingCancellation result = promoted.cancelBooking(promotedId, POLICY,
                    at(FREE_CANCELLATION_DEADLINE.plusSeconds(2)), EVERYONE);

            // Assert
            assertThat(result.isLate()).isTrue();
            assertThat(bookingOf(promoted, people.get(2)).confirmedAt()).contains(FREE_CANCELLATION_DEADLINE.plusSeconds(1));
        }

        @Test
        void theFreeCancellationDeadlineIsAnAbsoluteSixHoursAcrossTheDstChange() {
            // Arrange - 03:00 Lisbon (WEST) on 29 Mar 2026 is 02:00Z; six absolute hours earlier is 20:00Z on the 28th
            Instant start = Instant.parse("2026-03-29T02:00:00Z");
            Session session = SessionFactory.create(ASSOCIATION, GROUP, COACH, start, start.plus(Duration.ofMinutes(90)), 12);
            MemberId first = MemberId.generate();
            MemberId second = MemberId.generate();
            Instant bookedAt = Instant.parse("2026-03-22T03:00:00Z");
            Session booked = session.book(first, POLICY, at(bookedAt)).session();
            booked = booked.book(second, POLICY, at(bookedAt.plusSeconds(1))).session();
            Instant deadline = Instant.parse("2026-03-28T20:00:00Z");

            // Act
            BookingCancellation onTime = booked.cancelBooking(bookingOf(booked, first).id(), POLICY, at(deadline), EVERYONE);
            BookingCancellation late = booked.cancelBooking(bookingOf(booked, second).id(), POLICY,
                    at(deadline.plusSeconds(1)), EVERYONE);

            // Assert
            assertThat(onTime.isLate()).isFalse();
            assertThat(late.isLate()).isTrue();
        }

        @Test
        void rejectsAnUnknownBooking() {
            // Arrange
            Session session = bookedBy(2, members(1));
            Executable act = () -> session.cancelBooking(BookingId.generate(), POLICY, at(OPENS_AT), EVERYONE);

            // Act
            assertThrows(BookingNotFoundException.class, act);

            // Assert
            assertThat(session.bookings()).hasSize(1);
        }

        @Test
        void rejectsCancellingAnAlreadyCancelledBooking() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Session cancelled = session.cancelBooking(id, POLICY, at(OPENS_AT.plusSeconds(10)), EVERYONE).session();
            Executable act = () -> cancelled.cancelBooking(id, POLICY, at(OPENS_AT.plusSeconds(20)), EVERYONE);

            // Act
            assertThrows(InvalidBookingStatusTransitionException.class, act);

            // Assert
            assertThat(cancelled.freeSeats()).isEqualTo(2);
        }

        @Test
        void rejectsCancellingABookingOfASessionThatIsNotScheduled() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Session cancelledSession = session.cancel("Reason");
            Executable act = () -> cancelledSession.cancelBooking(id, POLICY, at(OPENS_AT.plusSeconds(10)), EVERYONE);

            // Act
            assertThrows(SessionNotScheduledException.class, act);

            // Assert
            assertThat(cancelledSession.status()).isEqualTo(SessionStatus.CANCELLED);
        }

        @Test
        void rejectsANullEligibilityPredicate() {
            // Arrange
            Session session = bookedBy(2, members(1));
            Executable act = () -> session.cancelBooking(session.bookings().get(0).id(), POLICY, at(OPENS_AT), null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("eligibleForPromotion");
        }
    }

    @Nested
    class CancelBookingByAssociation {
        @Test
        void cancelsAConfirmedBookingAsByAssociationAndFreesTheSeatForTheWaitlist() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBookingByAssociation(id, at(OPENS_AT.plusSeconds(100)), EVERYONE);

            // Assert
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.BY_ASSOCIATION);
            assertThat(result.cancelled().creditRefundable()).isTrue();
            assertThat(result.isLate()).isFalse();
            assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(people.get(1));
            assertThat(result.session().waitlist()).extracting(Booking::memberId).containsExactly(people.get(2));
        }

        @Test
        void isNeverLateEvenInsideTheLateWindow() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBookingByAssociation(id,
                    at(FREE_CANCELLATION_DEADLINE.plusSeconds(1)), EVERYONE);

            // Assert
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.BY_ASSOCIATION);
            assertThat(result.cancelled().creditRefundable()).isTrue();
        }

        @Test
        void isAllowedOneSecondBeforeTheStart() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBookingByAssociation(id, at(START.minusSeconds(1)), EVERYONE);

            // Assert
            assertThat(result.cancelled().status()).isEqualTo(BookingStatus.CANCELLED);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 1, 3600})
        void isRejectedAtOrAfterTheStart(long secondsAfterStart) {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Executable act = () -> session.cancelBookingByAssociation(id, at(START.plusSeconds(secondsAfterStart)), EVERYONE);

            // Act
            CancellationClosedException ex = assertThrows(CancellationClosedException.class, act);

            // Assert
            assertThat(ex.startsAt()).isEqualTo(START);
        }

        @Test
        void aWaitlistedBookingIsCancelledWithoutAnythingToRefund() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(1, people);
            BookingId waitlisted = bookingOf(session, people.get(1)).id();

            // Act
            BookingCancellation result = session.cancelBookingByAssociation(waitlisted, at(OPENS_AT.plusSeconds(100)), EVERYONE);

            // Assert
            assertThat(result.cancelled().cancellationKind()).contains(CancellationKind.BY_ASSOCIATION);
            assertThat(result.cancelled().creditRefundable()).isFalse();
            assertThat(result.promoted()).isEmpty();
        }

        @Test
        void skipsIneligibleWaitlistedMembersLikeAnyCancellation() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            BookingCancellation result = session.cancelBookingByAssociation(id, at(OPENS_AT.plusSeconds(100)),
                    member -> !member.equals(people.get(1)));

            // Assert
            assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(people.get(2));
        }

        @Test
        void rejectsAnAttendedBooking() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            Session marked = session.markAttended(bookingOf(session, people.get(0)).id(), at(START));
            BookingId id = bookingOf(marked, people.get(0)).id();
            Executable act = () -> marked.cancelBookingByAssociation(id, at(START.minusSeconds(1)), EVERYONE);

            // Act
            InvalidBookingStatusTransitionException ex = assertThrows(InvalidBookingStatusTransitionException.class, act);

            // Assert
            assertThat(ex).isNotNull();
        }

        @Test
        void rejectsASessionThatIsNotScheduledAnUnknownBookingAndANullPredicate() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Session cancelledSession = session.cancel("Reason");
            Executable notScheduled = () -> cancelledSession.cancelBookingByAssociation(id, at(OPENS_AT), EVERYONE);
            Executable unknown = () -> session.cancelBookingByAssociation(BookingId.generate(), at(OPENS_AT), EVERYONE);
            Executable nullPredicate = () -> session.cancelBookingByAssociation(id, at(OPENS_AT), null);

            // Act
            SessionNotScheduledException first = assertThrows(SessionNotScheduledException.class, notScheduled);
            BookingNotFoundException second = assertThrows(BookingNotFoundException.class, unknown);
            NullPointerException third = assertThrows(NullPointerException.class, nullPredicate);

            // Assert
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(third.getMessage()).contains("eligibleForPromotion");
        }
    }

    @Nested
    class AcceptsCancellations {
        @Test
        void isTrueWhileScheduledAndBeforeTheStart() {
            // Arrange
            Session session = newSession(2);

            // Act
            boolean wellBefore = session.acceptsCancellations(at(OPENS_AT));
            boolean lastSecond = session.acceptsCancellations(at(START.minusSeconds(1)));

            // Assert
            assertThat(wellBefore).isTrue();
            assertThat(lastSecond).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 1})
        void isFalseAtAndAfterTheStart(long secondsAfterStart) {
            // Arrange
            Session session = newSession(2);

            // Act
            boolean accepts = session.acceptsCancellations(at(START.plusSeconds(secondsAfterStart)));

            // Assert
            assertThat(accepts).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
        void isFalseOnceTheSessionIsNoLongerScheduled(SessionStatus status) {
            // Arrange
            Session session = sessionIn(status);

            // Act
            boolean accepts = session.acceptsCancellations(at(OPENS_AT));

            // Assert
            assertThat(accepts).isFalse();
        }
    }

    @Nested
    class Overlap {
        @Test
        void overlapsWhenTheIntervalsShareTime() {
            // Arrange
            Session session = newSession(2);
            Session later = SessionFactory.create(ASSOCIATION, GROUP, COACH, START.plus(Duration.ofMinutes(30)),
                    END.plus(Duration.ofMinutes(30)), 2);

            // Act
            boolean forward = session.overlaps(later);
            boolean backward = later.overlaps(session);

            // Assert
            assertThat(forward).isTrue();
            assertThat(backward).isTrue();
        }

        @Test
        void backToBackSessionsDoNotOverlap() {
            // Arrange
            Session session = newSession(2);
            Session next = SessionFactory.create(ASSOCIATION, GROUP, COACH, END, END.plus(Duration.ofMinutes(90)), 2);

            // Act
            boolean overlaps = session.overlaps(next);

            // Assert
            assertThat(overlaps).isFalse();
        }

        @Test
        void aSessionAlwaysOverlapsItselfAndASeparateOneDoesNot() {
            // Arrange
            Session session = newSession(2);
            Session dayAfter = SessionFactory.create(ASSOCIATION, GROUP, COACH, START.plus(Duration.ofDays(1)),
                    END.plus(Duration.ofDays(1)), 2);

            // Act
            boolean itself = session.overlaps(session);
            boolean apart = session.overlaps(dayAfter);

            // Assert
            assertThat(itself).isTrue();
            assertThat(apart).isFalse();
        }
    }

    @Nested
    class NoOverlapRule {
        private Session other(Instant start, Instant end) {
            return SessionFactory.create(ASSOCIATION, GROUP, COACH, start, end, 2);
        }

        @Test
        void rejectsWhenAnotherSessionOfTheMemberOverlaps() {
            // Arrange
            Session session = newSession(2);
            Session clash = other(START.plus(Duration.ofMinutes(30)), END.plus(Duration.ofMinutes(30)));
            Executable act = () -> session.requireNoOverlapWith(List.of(clash));

            // Act
            BookingOverlapException ex = assertThrows(BookingOverlapException.class, act);

            // Assert
            assertThat(ex.overlappingSessionId()).isEqualTo(clash.id());
            assertThat(ex.getMessage()).contains("20/10/2026 20:30");
        }

        @Test
        void ignoresTheSessionItselfBackToBackAndDistantSessions() {
            // Arrange
            Session session = newSession(2);
            Session before = other(START.minus(Duration.ofMinutes(90)), START);
            Session after = other(END, END.plus(Duration.ofMinutes(90)));
            Session nextWeek = other(START.plus(Duration.ofDays(7)), END.plus(Duration.ofDays(7)));

            // Act
            Executable act = () -> session.requireNoOverlapWith(List.of(session, before, after, nextWeek));

            // Assert
            assertDoesNotThrow(act);
        }

        @Test
        void aCancelledSessionNeverBlocks() {
            // Arrange
            Session session = newSession(2);
            Session cancelled = other(START, END).cancel("Venue closed");

            // Act
            Executable act = () -> session.requireNoOverlapWith(List.of(cancelled));

            // Assert
            assertDoesNotThrow(act);
        }

        @Test
        void nothingToCompareAgainstIsFine() {
            // Arrange
            Session session = newSession(2);

            // Act
            Executable act = () -> session.requireNoOverlapWith(List.of());

            // Assert
            assertDoesNotThrow(act);
        }
    }

    @Nested
    class FindBooking {
        @Test
        void findsABookingByIdOrReturnsEmpty() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            Booking booking = bookingOf(session, people.get(0));

            // Act
            var found = session.findBooking(booking.id());
            var missing = session.findBooking(BookingId.generate());

            // Assert
            assertThat(found).contains(booking);
            assertThat(missing).isEmpty();
        }

        @Test
        void activeBookingOfIsTheMembersWaitlistedOrConfirmedBookingOnly() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            Session withCancelled = session.cancelBooking(bookingOf(session, people.get(1)).id(), POLICY,
                    at(OPENS_AT.plusSeconds(100)), member -> false).session();

            // Act
            var confirmed = withCancelled.activeBookingOf(people.get(0));
            var waitlisted = withCancelled.activeBookingOf(people.get(2));
            var cancelled = withCancelled.activeBookingOf(people.get(1));
            var stranger = withCancelled.activeBookingOf(MemberId.generate());

            // Assert
            assertThat(confirmed).hasValueSatisfying(b -> assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED));
            assertThat(waitlisted).hasValueSatisfying(b -> assertThat(b.status()).isEqualTo(BookingStatus.WAITLISTED));
            assertThat(cancelled).isEmpty();
            assertThat(stranger).isEmpty();
        }

        @Test
        void activeBookingOfIgnoresAttendedAndNoShowBookings() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            Session attended = session.markAttended(bookingOf(session, people.get(0)).id(), at(START));

            // Act
            var active = attended.activeBookingOf(people.get(0));

            // Assert
            assertThat(active).isEmpty();
        }
    }

    @Nested
    class ChangeCapacity {
        @Test
        void raisesTheCapacityWithoutAWaitlist() {
            // Arrange
            Session session = bookedBy(2, members(2));

            // Act
            CapacityChange change = session.changeCapacity(5, PROMOTION_CLOCK, EVERYONE);

            // Assert
            assertThat(change.session().capacity()).isEqualTo(5);
            assertThat(change.session().freeSeats()).isEqualTo(3);
            assertThat(change.promoted()).isEmpty();
            assertThat(session.capacity()).isEqualTo(2);
        }

        @Test
        void raisingPromotesTheWaitlistInArrivalOrderUpToTheNewSeats() {
            // Arrange
            List<MemberId> people = members(5);
            Session session = bookedBy(2, people);

            // Act
            CapacityChange change = session.changeCapacity(4, PROMOTION_CLOCK, EVERYONE);

            // Assert
            assertThat(change.promoted()).extracting(Booking::memberId)
                    .containsExactly(people.get(2), people.get(3));
            assertThat(change.session().confirmedCount()).isEqualTo(4);
            assertThat(change.session().waitlist()).extracting(Booking::memberId).containsExactly(people.get(4));
        }

        @Test
        void raisingBeyondTheWaitlistLeavesTheRestFree() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(2, people);

            // Act
            CapacityChange change = session.changeCapacity(10, PROMOTION_CLOCK, EVERYONE);

            // Assert
            assertThat(change.promoted()).hasSize(1);
            assertThat(change.session().freeSeats()).isEqualTo(7);
            assertThat(change.session().waitlist()).isEmpty();
        }

        @Test
        void raisingSkipsIneligibleWaitlistedMembers() {
            // Arrange
            List<MemberId> people = members(5);
            Session session = bookedBy(2, people);
            Predicate<MemberId> noBalance = member -> !member.equals(people.get(2));

            // Act
            CapacityChange change = session.changeCapacity(4, PROMOTION_CLOCK, noBalance);

            // Assert
            assertThat(change.promoted()).extracting(Booking::memberId)
                    .containsExactly(people.get(3), people.get(4));
            assertThat(change.session().waitlist()).extracting(Booking::memberId).containsExactly(people.get(2));
        }

        @Test
        void loweringToExactlyTheConfirmedCountIsAllowed() {
            // Arrange
            Session session = bookedBy(10, members(4));

            // Act
            CapacityChange change = session.changeCapacity(4, PROMOTION_CLOCK, EVERYONE);

            // Assert
            assertThat(change.session().capacity()).isEqualTo(4);
            assertThat(change.session().freeSeats()).isZero();
            assertThat(change.promoted()).isEmpty();
        }

        @Test
        void loweringBelowTheConfirmedCountIsRejected() {
            // Arrange
            Session session = bookedBy(10, members(4));
            Executable act = () -> session.changeCapacity(3, PROMOTION_CLOCK, EVERYONE);

            // Act
            CapacityBelowConfirmedException ex = assertThrows(CapacityBelowConfirmedException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("3").contains("4");
            assertThat(session.capacity()).isEqualTo(10);
        }

        @Test
        void waitlistedAndCancelledBookingsDoNotCountAsConfirmed() {
            // Arrange
            List<MemberId> people = members(4);
            Session full = bookedBy(2, people);
            BookingId cancelledId = bookingOf(full, people.get(0)).id();
            Session session = full.cancelBooking(cancelledId, POLICY, at(OPENS_AT.plusSeconds(100)), member -> false).session();

            // Act
            CapacityChange change = session.changeCapacity(1, PROMOTION_CLOCK, member -> false);

            // Assert
            assertThat(change.session().capacity()).isEqualTo(1);
            assertThat(change.session().confirmedCount()).isEqualTo(1);
        }

        @Test
        void allowsChangingTheCapacityOneSecondBeforeTheStart() {
            // Arrange
            Session session = bookedBy(2, members(3));

            // Act
            CapacityChange change = session.changeCapacity(3, at(START.minusSeconds(1)), EVERYONE);

            // Assert
            assertThat(change.session().capacity()).isEqualTo(3);
            assertThat(change.promoted()).hasSize(1);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 1, 3600})
        void rejectsChangingTheCapacityAtOrAfterTheStart(long secondsAfterStart) {
            // Arrange
            Session session = bookedBy(2, members(3));
            Executable act = () -> session.changeCapacity(5, at(START.plusSeconds(secondsAfterStart)), EVERYONE);

            // Act
            SessionAlreadyStartedException ex = assertThrows(SessionAlreadyStartedException.class, act);

            // Assert
            assertThat(ex.startsAt()).isEqualTo(START);
            assertThat(session.capacity()).isEqualTo(2);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void rejectsANonPositiveCapacity(int capacity) {
            // Arrange
            Session session = newSession(12);
            Executable act = () -> session.changeCapacity(capacity, PROMOTION_CLOCK, EVERYONE);

            // Act
            InvalidCapacityException ex = assertThrows(InvalidCapacityException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("Capacity must be greater than zero");
        }

        @Test
        void doesNotEnforceAMultipleOfSix() {
            // Arrange
            Session session = newSession(12);

            // Act
            CapacityChange change = session.changeCapacity(7, PROMOTION_CLOCK, EVERYONE);

            // Assert
            assertThat(change.session().capacity()).isEqualTo(7);
        }

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
        void rejectsChangingTheCapacityOfASessionThatIsNotScheduled(SessionStatus status) {
            // Arrange
            Session session = sessionIn(status);
            Executable act = () -> session.changeCapacity(20, PROMOTION_CLOCK, EVERYONE);

            // Act
            assertThrows(SessionNotScheduledException.class, act);

            // Assert
            assertThat(session.capacity()).isEqualTo(12);
        }

        @Test
        void rejectsANullEligibilityPredicate() {
            // Arrange
            Session session = newSession(12);
            Executable act = () -> session.changeCapacity(20, PROMOTION_CLOCK, null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("eligibleForPromotion");
        }
    }

    @Nested
    class CancelSession {
        @Test
        void cancelsTheSessionAndEveryActiveBookingFlaggedAsBySession() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(2, people);

            // Act
            Session cancelled = session.cancel("Venue unavailable");

            // Assert
            assertThat(cancelled.status()).isEqualTo(SessionStatus.CANCELLED);
            assertThat(cancelled.cancellationReason()).contains("Venue unavailable");
            assertThat(cancelled.bookings()).hasSize(3)
                    .allSatisfy(b -> {
                        assertThat(b.status()).isEqualTo(BookingStatus.CANCELLED);
                        assertThat(b.cancellationKind()).contains(CancellationKind.BY_SESSION);
                    });
            assertThat(cancelled.bookingsCancelledBySession()).hasSize(3);
            assertThat(cancelled.confirmedCount()).isZero();
            assertThat(session.status()).isEqualTo(SessionStatus.SCHEDULED);
        }

        @Test
        void refundsOnlyTheBookingsThatHeldASeatAndNotifiesEveryone() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(2, people);

            // Act
            Session cancelled = session.cancel("Coach injured");

            // Assert
            assertThat(cancelled.bookingsCancelledBySession()).extracting(Booking::memberId)
                    .containsExactlyInAnyOrder(people.get(0), people.get(1), people.get(2));
            assertThat(cancelled.bookingsToRefund()).extracting(Booking::memberId)
                    .containsExactlyInAnyOrder(people.get(0), people.get(1));
            assertThat(cancelled.bookingsToRefund()).allMatch(Booking::creditRefundable);
            assertThat(bookingOf(cancelled, people.get(2)).consumedCredit()).isFalse();
        }

        @Test
        void aPromotedBookingHeldASeatSoItIsRefunded() {
            // Arrange
            List<MemberId> people = members(3);
            Session full = bookedBy(2, people);
            Session promoted = full.cancelBooking(bookingOf(full, people.get(0)).id(), POLICY,
                    at(OPENS_AT.plusSeconds(100)), EVERYONE).session();

            // Act
            Session cancelled = promoted.cancel("Coach injured");

            // Assert
            assertThat(cancelled.bookingsToRefund()).extracting(Booking::memberId)
                    .containsExactlyInAnyOrder(people.get(1), people.get(2));
        }

        @Test
        void keepsEarlierCancellationsAsTheyWereSoALateCancellationIsNotRefunded() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(3, people);
            Session lateCancelled = session.cancelBooking(bookingOf(session, people.get(0)).id(), POLICY,
                    at(START.minusSeconds(1)), EVERYONE).session();
            Session freeCancelled = lateCancelled.cancelBooking(bookingOf(lateCancelled, people.get(1)).id(), POLICY,
                    at(START.minusSeconds(1)), EVERYONE).session();

            // Act
            Session cancelled = freeCancelled.cancel("Coach injured");

            // Assert
            assertThat(bookingOf(cancelled, people.get(0)).cancellationKind()).contains(CancellationKind.LATE);
            assertThat(bookingOf(cancelled, people.get(1)).cancellationKind()).contains(CancellationKind.LATE);
            assertThat(bookingOf(cancelled, people.get(2)).cancellationKind()).contains(CancellationKind.BY_SESSION);
            assertThat(cancelled.bookingsToRefund()).extracting(Booking::memberId).containsExactly(people.get(2));
            assertThat(cancelled.bookingsCancelledBySession()).extracting(Booking::memberId)
                    .containsExactly(people.get(2));
        }

        @Test
        void leavesFreeCancellationsUntouched() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(2, people);
            Session withFree = session.cancelBooking(bookingOf(session, people.get(0)).id(), POLICY,
                    at(OPENS_AT.plusSeconds(100)), EVERYONE).session();

            // Act
            Session cancelled = withFree.cancel("Coach injured");

            // Assert
            assertThat(bookingOf(cancelled, people.get(0)).cancellationKind()).contains(CancellationKind.FREE);
            assertThat(cancelled.bookingsToRefund()).extracting(Booking::memberId).containsExactly(people.get(1));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void rejectsCancellingOnceAttendanceWasMarked(boolean attended) {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Session marked = attended ? session.markAttended(id, at(START)) : session.markNoShow(id, at(START));
            Executable act = () -> marked.cancel("Coach injured");

            // Act
            assertThrows(AttendanceAlreadyMarkedException.class, act);

            // Assert
            assertThat(marked.status()).isEqualTo(SessionStatus.SCHEDULED);
            assertThat(bookingOf(marked, people.get(1)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   \t"})
        void rejectsABlankReason(String reason) {
            // Arrange
            Session session = bookedBy(2, members(1));
            Executable act = () -> session.cancel(reason);

            // Act
            CancellationReasonRequiredException ex = assertThrows(CancellationReasonRequiredException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("reason");
            assertThat(session.status()).isEqualTo(SessionStatus.SCHEDULED);
        }

        @Test
        void doesNotListBookingsOfASessionThatWasNotCancelled() {
            // Arrange
            Session session = bookedBy(2, members(2));

            // Act
            List<Booking> bySession = session.bookingsCancelledBySession();

            // Assert
            assertThat(bySession).isEmpty();
        }
    }

    @Nested
    class CompleteSession {
        @Test
        void completesExactlyAtTheStart() {
            // Arrange
            Session session = newSession(12);

            // Act
            Session completed = session.complete(at(START));

            // Assert
            assertThat(completed.status()).isEqualTo(SessionStatus.COMPLETED);
        }

        @Test
        void completesAfterTheStart() {
            // Arrange
            Session session = newSession(12);

            // Act
            Session completed = session.complete(at(END.plusSeconds(60)));

            // Assert
            assertThat(completed.status()).isEqualTo(SessionStatus.COMPLETED);
        }

        @Test
        void cancelsStillWaitlistedBookingsAsNotPromotedAndLeavesTheRestAsTheyAre() {
            // Arrange
            List<MemberId> people = members(3);
            Session session = bookedBy(1, people);
            BookingId attendedId = bookingOf(session, people.get(0)).id();
            Session marked = session.markAttended(attendedId, at(START));

            // Act
            Session completed = marked.complete(at(START));

            // Assert
            assertThat(bookingOf(completed, people.get(0)).status()).isEqualTo(BookingStatus.ATTENDED);
            assertThat(bookingOf(completed, people.get(1)).status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(bookingOf(completed, people.get(1)).cancellationKind()).contains(CancellationKind.NOT_PROMOTED);
            assertThat(bookingOf(completed, people.get(2)).cancellationKind()).contains(CancellationKind.NOT_PROMOTED);
            assertThat(completed.waitlist()).isEmpty();
            assertThat(completed.bookingsToRefund()).isEmpty();
        }

        @Test
        void leavesUnmarkedConfirmedBookingsConfirmed() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(2, people);

            // Act
            Session completed = session.complete(at(END));

            // Assert
            assertThat(completed.bookings()).extracting(Booking::status)
                    .containsOnly(BookingStatus.CONFIRMED);
        }

        @Test
        void rejectsCompletingOneSecondBeforeTheStart() {
            // Arrange
            Session session = newSession(12);
            Executable act = () -> session.complete(at(START.minusSeconds(1)));

            // Act
            SessionNotStartedException ex = assertThrows(SessionNotStartedException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("starts at 20/10/2026 20:00");
            assertThat(session.status()).isEqualTo(SessionStatus.SCHEDULED);
        }
    }

    @Nested
    class Attendance {
        @Test
        void marksAConfirmedBookingAttendedFromTheStart() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();

            // Act
            Session marked = session.markAttended(id, at(START));

            // Assert
            assertThat(bookingOf(marked, people.get(0)).status()).isEqualTo(BookingStatus.ATTENDED);
            assertThat(bookingOf(marked, people.get(1)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void marksAConfirmedBookingNoShowFromTheStart() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(1)).id();

            // Act
            Session marked = session.markNoShow(id, at(START));

            // Assert
            assertThat(bookingOf(marked, people.get(1)).status()).isEqualTo(BookingStatus.NO_SHOW);
            assertThat(bookingOf(marked, people.get(0)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void allowsMarkingAfterTheSessionWasCompleted() {
            // Arrange
            List<MemberId> people = members(1);
            Session completed = bookedBy(2, people).complete(at(END));
            BookingId id = bookingOf(completed, people.get(0)).id();

            // Act
            Session marked = completed.markNoShow(id, at(END.plusSeconds(600)));

            // Assert
            assertThat(bookingOf(marked, people.get(0)).status()).isEqualTo(BookingStatus.NO_SHOW);
        }

        @Test
        void rejectsMarkingAttendedOneSecondBeforeTheStart() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Executable act = () -> session.markAttended(id, at(START.minusSeconds(1)));

            // Act
            assertThrows(SessionNotStartedException.class, act);

            // Assert
            assertThat(bookingOf(session, people.get(0)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void rejectsMarkingNoShowBeforeTheStart() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Executable act = () -> session.markNoShow(id, at(START.minusSeconds(1)));

            // Act
            assertThrows(SessionNotStartedException.class, act);

            // Assert
            assertThat(bookingOf(session, people.get(0)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void rejectsMarkingAWaitlistedBooking() {
            // Arrange
            List<MemberId> people = members(2);
            Session session = bookedBy(1, people);
            BookingId waitlisted = bookingOf(session, people.get(1)).id();
            Executable act = () -> session.markAttended(waitlisted, at(START));

            // Act
            assertThrows(InvalidBookingStatusTransitionException.class, act);

            // Assert
            assertThat(bookingOf(session, people.get(1)).status()).isEqualTo(BookingStatus.WAITLISTED);
        }

        @Test
        void rejectsMarkingTheSameBookingTwice() {
            // Arrange
            List<MemberId> people = members(1);
            Session session = bookedBy(2, people);
            BookingId id = bookingOf(session, people.get(0)).id();
            Session marked = session.markAttended(id, at(START));
            Executable act = () -> marked.markNoShow(id, at(START));

            // Act
            assertThrows(InvalidBookingStatusTransitionException.class, act);

            // Assert
            assertThat(bookingOf(marked, people.get(0)).status()).isEqualTo(BookingStatus.ATTENDED);
        }

        @Test
        void rejectsMarkingAnUnknownBooking() {
            // Arrange
            Session session = bookedBy(2, members(1));
            Executable act = () -> session.markAttended(BookingId.generate(), at(START));

            // Act
            assertThrows(BookingNotFoundException.class, act);

            // Assert
            assertThat(session.bookings()).hasSize(1);
        }
    }

    @Nested
    class PromoteWaitlist {
        @Test
        void promotesEligibleWaitlistedBookingsIntoFreeSeatsInOrder() {
            // Arrange
            List<MemberId> people = members(4);
            Session full = bookedBy(1, people);
            Session withFreeSeats = full.changeCapacity(3, PROMOTION_CLOCK, member -> false).session();
            Predicate<MemberId> regainedBalance = member -> !member.equals(people.get(1));

            // Act
            WaitlistPromotion promotion = withFreeSeats.promoteWaitlist(at(OPENS_AT.plusSeconds(900)), regainedBalance);

            // Assert
            assertThat(withFreeSeats.freeSeats()).isEqualTo(2);
            assertThat(promotion.promoted()).extracting(Booking::memberId).containsExactly(people.get(2), people.get(3));
            assertThat(promotion.promoted()).allSatisfy(b ->
                    assertThat(b.confirmedAt()).contains(OPENS_AT.plusSeconds(900)));
            assertThat(promotion.session().waitlist()).extracting(Booking::memberId).containsExactly(people.get(1));
        }

        @Test
        void promotesNobodyWhenNoSeatIsFree() {
            // Arrange
            Session session = bookedBy(1, members(2));

            // Act
            WaitlistPromotion promotion = session.promoteWaitlist(at(OPENS_AT.plusSeconds(900)), EVERYONE);

            // Assert
            assertThat(promotion.promoted()).isEmpty();
            assertThat(promotion.session().waitlist()).hasSize(1);
        }

        @Test
        void isANoOpAtTheStart() {
            // Arrange
            Session session = bookedBy(1, members(2));
            Session freed = session.changeCapacity(2, PROMOTION_CLOCK, member -> false).session();

            // Act
            WaitlistPromotion promotion = freed.promoteWaitlist(at(START), EVERYONE);

            // Assert
            assertThat(promotion.session()).isSameAs(freed);
            assertThat(promotion.promoted()).isEmpty();
            assertThat(promotion.session().waitlist()).hasSize(1);
        }

        @Test
        void promotesOneSecondBeforeTheStart() {
            // Arrange
            Session session = bookedBy(1, members(1));

            // Act
            WaitlistPromotion promotion = session.promoteWaitlist(at(START.minusSeconds(1)), EVERYONE);

            // Assert
            assertThat(promotion.promoted()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
        void isANoOpWhenTheSessionIsNotScheduled(SessionStatus status) {
            // Arrange
            Session session = sessionIn(status);

            // Act
            WaitlistPromotion promotion = session.promoteWaitlist(at(OPENS_AT), EVERYONE);

            // Assert
            assertThat(promotion.session()).isSameAs(session);
            assertThat(promotion.promoted()).isEmpty();
        }

        @Test
        void rejectsANullEligibilityPredicate() {
            // Arrange
            Session session = newSession(12);
            Executable act = () -> session.promoteWaitlist(at(OPENS_AT), null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("eligibleForPromotion");
        }
    }
}
