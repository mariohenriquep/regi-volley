package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidSessionException;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ScheduledOccurrence;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionFactoryTest {

    private static final LevelId INTERMEDIATE = LevelId.generate();

    private static final VenueId VENUE = VenueId.generate();

    private static final WeeklySlot MONDAY_8PM = slot(DayOfWeek.MONDAY, 20, 0, 90);

    private static final WeeklySlot WEDNESDAY_9PM = slot(DayOfWeek.WEDNESDAY, 21, 0, 90);

    /** What the use case does: the group says which occurrences are missing, the factory builds their sessions. */
    private static List<Session> generate(TrainingGroup group, Instant from, SessionGenerationPolicy policy,
                                          List<Session> existing) {
        return SessionFactory.createSessionsFor(group, from, policy, existing);
    }

    private static LocalTime lisbonTime(Session session) {
        return session.startsAt().atZone(ZoneId.of("Europe/Lisbon")).toLocalTime();
    }

    private static TrainingGroup group(WeeklySchedule schedule) {
        return TrainingGroupFactory.create(ASSOCIATION, "Open play", Set.of(INTERMEDIATE), VENUE, schedule, 12, COACH);
    }

    private static TrainingGroup group(String name, Set<LevelId> levels, int capacity) {
        return TrainingGroupFactory.create(ASSOCIATION, name, levels, VENUE, WeeklySchedule.of(MONDAY_8PM), capacity, COACH);
    }

    private static WeeklySlot slot(DayOfWeek day, int hour, int minute, long minutes) {
        return new WeeklySlot(day, LocalTime.of(hour, minute), Duration.ofMinutes(minutes));
    }

    private static final SessionGenerationPolicy FOUR_WEEKS = SessionGenerationPolicy.defaults();

    // Monday 12/01/2026 00:00Z: the four-week window runs to Monday 09/02/2026
    private static final Instant WINTER_FROM = Instant.parse("2026-01-12T00:00:00Z");

    // 20:00 Lisbon (WEST, UTC+1) on Tuesday 20 Oct 2026; DST ends on 25 Oct, so the window opens
    // at the same wall-clock time on 13 Oct.
    private static final Instant START = Instant.parse("2026-10-20T19:00:00Z");

    private static final Instant END = START.plus(Duration.ofMinutes(90));

    private static final Instant OPENS_AT = Instant.parse("2026-10-13T19:00:00Z");

    private static final AssociationId ASSOCIATION = AssociationId.generate();

    private static final TrainingGroupId GROUP = TrainingGroupId.generate();

    private static final MemberId COACH = MemberId.generate();

    private static final BookingPolicy POLICY = BookingPolicy.defaults();

    private static Session newSession(int capacity) {
        return SessionFactory.create(ASSOCIATION, GROUP, COACH, START, END, capacity);
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

    @Test
    void requiresAGroup() {
        // Arrange
        Executable act = () -> SessionFactory.createSessionsFor(null, WINTER_FROM, FOUR_WEEKS, List.of());

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("group");
    }

    @Test
    void createsAScheduledSessionWithNoBookings() {
        // Arrange
        int capacity = 12;

        // Act
        Session session = SessionFactory.create(ASSOCIATION, GROUP, COACH, START, END, capacity);

        // Assert
        assertThat(session.id()).isNotNull();
        assertThat(session.associationId()).isEqualTo(ASSOCIATION);
        assertThat(session.trainingGroupId()).isEqualTo(GROUP);
        assertThat(session.coachId()).isEqualTo(COACH);
        assertThat(session.startsAt()).isEqualTo(START);
        assertThat(session.endsAt()).isEqualTo(END);
        assertThat(session.capacity()).isEqualTo(12);
        assertThat(session.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(session.cancellationReason()).isEmpty();
        assertThat(session.bookings()).isEmpty();
        assertThat(session.freeSeats()).isEqualTo(12);
        assertThat(session.bookingOpensAt(POLICY)).isEqualTo(OPENS_AT);
        assertThat(session.version()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsAnEndThatIsNotAfterTheStart(long secondsAfterStart) {
        // Arrange
        Instant end = START.plusSeconds(secondsAfterStart);
        Executable act = () -> SessionFactory.create(ASSOCIATION, GROUP, COACH, START, end, 12);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("end");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void rejectsANonPositiveCapacity(int capacity) {
        // Arrange
        Executable act = () -> newSession(capacity);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("capacity");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 11, 13})
    void doesNotEnforceAMultipleOfSix(int capacity) {
        // Arrange
        // (capacity provided by the parameter)

        // Act
        Session session = assertDoesNotThrow(() -> newSession(capacity));

        // Assert
        assertThat(session.capacity()).isEqualTo(capacity);
    }

    @Test
    void rejectsANullCoach() {
        // Arrange
        Executable act = () -> SessionFactory.create(ASSOCIATION, GROUP, null, START, END, 12);

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("coachId");
    }

    @Test
    void reconstructRejectsCancelledWithoutReason() {
        // Arrange
        Executable act = () -> SessionFactory.reconstitute(SessionId.generate(), ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.CANCELLED, null, List.of(), 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("cancellation reason");
    }

    @Test
    void reconstructRejectsAReasonOnANonCancelledSession() {
        // Arrange
        Executable act = () -> SessionFactory.reconstitute(SessionId.generate(), ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.SCHEDULED, "reason", List.of(), 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("cancellation reason");
    }

    @Test
    void reconstructRejectsABookingFromAnotherSession() {
        // Arrange
        Booking foreign = bookingOf(ASSOCIATION, SessionId.generate(), MemberId.generate(),
                BookingStatus.CONFIRMED, OPENS_AT);
        Executable act = () -> SessionFactory.reconstitute(SessionId.generate(), ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.SCHEDULED, null, List.of(foreign), 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("belong");
    }

    @Test
    void reconstructRejectsABookingFromAnotherAssociation() {
        // Arrange
        SessionId sessionId = SessionId.generate();
        Booking foreign = bookingOf(AssociationId.generate(), sessionId, MemberId.generate(),
                BookingStatus.CONFIRMED, OPENS_AT);
        Executable act = () -> SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.SCHEDULED, null, List.of(foreign), 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("belong");
    }

    @Test
    void reconstructRejectsMoreConfirmedBookingsThanCapacity() {
        // Arrange
        SessionId sessionId = SessionId.generate();
        List<Booking> confirmed = members(2).stream()
                .map(m -> bookingOf(ASSOCIATION, sessionId, m, BookingStatus.CONFIRMED, OPENS_AT))
                .toList();
        Executable act = () -> SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END,
                1, SessionStatus.SCHEDULED, null, confirmed, 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("exceed");
    }

    @Test
    void reconstructRejectsTwoNonCancelledBookingsOfTheSameMember() {
        // Arrange
        SessionId sessionId = SessionId.generate();
        MemberId member = MemberId.generate();
        List<Booking> twice = List.of(
                bookingOf(ASSOCIATION, sessionId, member, BookingStatus.CONFIRMED, OPENS_AT),
                bookingOf(ASSOCIATION, sessionId, member, BookingStatus.WAITLISTED, OPENS_AT.plusSeconds(1)));
        Executable act = () -> SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.SCHEDULED, null, twice, 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("at most one");
    }

    @Test
    void reconstructAllowsACancelledBookingNextToALiveOneOfTheSameMember() {
        // Arrange
        SessionId sessionId = SessionId.generate();
        MemberId member = MemberId.generate();
        Booking first = SessionFactory.reconstituteBooking(BookingId.generate(), ASSOCIATION, sessionId, member,
                BookingStatus.CANCELLED, OPENS_AT, null, CancellationKind.FREE);
        Booking second = bookingOf(ASSOCIATION, sessionId, member, BookingStatus.CONFIRMED, OPENS_AT.plusSeconds(1));

        // Act
        Session session = SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.SCHEDULED, null, List.of(first, second), 0L);

        // Assert
        assertThat(session.bookings()).hasSize(2);
    }

    @Test
    void reconstructRejectsALiveBookingOfTheCoach() {
        // Arrange
        SessionId sessionId = SessionId.generate();
        Booking coachBooking = bookingOf(ASSOCIATION, sessionId, COACH, BookingStatus.CONFIRMED, OPENS_AT);
        Executable act = () -> SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END,
                12, SessionStatus.SCHEDULED, null, List.of(coachBooking), 0L);

        // Act
        InvalidSessionException ex = assertThrows(InvalidSessionException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("coach");
    }

    @Test
    void reconstructKeepsTheGivenState() {
        // Arrange
        SessionId sessionId = SessionId.generate();
        Booking booking = bookingOf(ASSOCIATION, sessionId, MemberId.generate(), BookingStatus.CONFIRMED,
                OPENS_AT);

        // Act
        Session session = SessionFactory.reconstitute(sessionId, ASSOCIATION, GROUP, COACH, START, END, 12,
                SessionStatus.COMPLETED, null, List.of(booking), 7L);

        // Assert
        assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.bookings()).containsExactly(booking);
        assertThat(session.version()).isEqualTo(7L);
    }

    @Test
    void generatesEveryOccurrenceInTheWindowForAMultiSlotSchedule() {
        // Arrange - Mon+Wed 20:00 / 21:00 over 12/01 .. 09/02/2026 (exclusive)
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM, WEDNESDAY_9PM));

        // Act
        List<Session> sessions = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());

        // Assert
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-01-12T20:00:00Z", "2026-01-14T21:00:00Z",
                "2026-01-19T20:00:00Z", "2026-01-21T21:00:00Z",
                "2026-01-26T20:00:00Z", "2026-01-28T21:00:00Z",
                "2026-02-02T20:00:00Z", "2026-02-04T21:00:00Z");
    }

    @Test
    void generatedSessionsInheritTheGroupsCapacityCoachAndOwnership() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));

        // Act
        List<Session> sessions = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());

        // Assert
        assertThat(sessions).hasSize(4).allSatisfy(s -> {
            assertThat(s.associationId()).isEqualTo(ASSOCIATION);
            assertThat(s.trainingGroupId()).isEqualTo(group.id());
            assertThat(s.coachId()).isEqualTo(COACH);
            assertThat(s.capacity()).isEqualTo(12);
            assertThat(s.status()).isEqualTo(SessionStatus.SCHEDULED);
            assertThat(s.bookings()).isEmpty();
            assertThat(Duration.between(s.startsAt(), s.endsAt())).isEqualTo(Duration.ofMinutes(90));
        });
    }

    @Test
    void rerunningGenerationCreatesNothingNew() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM, WEDNESDAY_9PM));
        List<Session> first = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());

        // Act
        List<Session> second = generate(group, WINTER_FROM, FOUR_WEEKS, first);

        // Assert
        assertThat(second).isEmpty();
    }

    @Test
    void generatesOnlyTheMissingSessionsWhenSomeAlreadyExist() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        List<Session> all = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());
        List<Session> existing = List.of(all.get(0), all.get(2));

        // Act
        List<Session> generated = generate(group, WINTER_FROM, FOUR_WEEKS, existing);

        // Assert
        assertThat(generated).extracting(Session::startsAt)
                .containsExactly(all.get(1).startsAt(), all.get(3).startsAt());
    }

    @Test
    void generatesOnlyTheNewWeeksWhenTheWindowAdvances() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        List<Session> existing = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());
        Instant aWeekLater = WINTER_FROM.plus(Duration.ofDays(7));

        // Act
        List<Session> generated = generate(group, aWeekLater, FOUR_WEEKS, existing);

        // Assert
        assertThat(generated).extracting(s -> s.startsAt().toString()).containsExactly("2026-02-09T20:00:00Z");
    }

    @Test
    void aCancelledSessionIsNotGeneratedAgain() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        List<Session> all = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());
        Session cancelled = all.get(0).cancel("Venue closed");

        // Act
        List<Session> generated = generate(group, WINTER_FROM, FOUR_WEEKS, List.of(cancelled));

        // Assert
        assertThat(generated).extracting(Session::startsAt)
                .doesNotContain(cancelled.startsAt())
                .hasSize(3);
    }

    @Test
    void sessionsOfOtherGroupsDoNotCountAsExisting() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        Instant start = Instant.parse("2026-01-12T20:00:00Z");
        Session otherGroup = SessionFactory.create(ASSOCIATION, TrainingGroupId.generate(), COACH, start,
                start.plus(Duration.ofMinutes(90)), 12);

        // Act
        List<Session> generated = generate(group, WINTER_FROM, FOUR_WEEKS, List.of(otherGroup));

        // Assert
        assertThat(generated).hasSize(4);
    }

    @Test
    void rejectsExistingSessionsOfAnotherAssociation() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        Instant start = Instant.parse("2026-01-12T20:00:00Z");
        Session foreign = SessionFactory.create(AssociationId.generate(), group.id(), COACH, start,
                start.plus(Duration.ofMinutes(90)), 12);
        Executable act = () -> generate(group, WINTER_FROM, FOUR_WEEKS, List.of(foreign));

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("association");
    }

    @Test
    void neverEmitsTwoSessionsWithTheSameStartInOneBatch() {
        // Arrange - on 29/03/2026 the non-existent 01:30 is moved to 02:30, the same instant as the 02:30 slot
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60), slot(DayOfWeek.SUNDAY, 2, 30, 60)));
        Instant from = Instant.parse("2026-03-23T00:00:00Z");

        // Act
        List<Session> sessions = generate(group, from, new SessionGenerationPolicy(2), List.of());

        // Assert - 29/03 once, then both slots on 05/04
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-03-29T01:30:00Z", "2026-04-05T00:30:00Z", "2026-04-05T01:30:00Z");
        assertThat(sessions).extracting(Session::startsAt).doesNotHaveDuplicates();
    }

    @Test
    void collidingSlotsStayIdempotentOnRerun() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60), slot(DayOfWeek.SUNDAY, 2, 30, 60)));
        Instant from = Instant.parse("2026-03-23T00:00:00Z");
        List<Session> first = generate(group, from, new SessionGenerationPolicy(2), List.of());

        // Act
        List<Session> second = generate(group, from, new SessionGenerationPolicy(2), first);

        // Assert
        assertThat(second).isEmpty();
    }

    @Test
    void anArchivedGroupGeneratesNothing() {
        // Arrange
        TrainingGroup archived = group(WeeklySchedule.of(MONDAY_8PM)).archive();

        // Act
        List<Session> generated = generate(archived, WINTER_FROM, FOUR_WEEKS, List.of());

        // Assert
        assertThat(generated).isEmpty();
    }

    @Test
    void includesASlotExactlyAtTheWindowStartAndExcludesOneExactlyAtItsEnd() {
        // Arrange - from = Monday 20:00 itself; the window ends Monday 09/02 20:00 exactly
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        Instant from = Instant.parse("2026-01-12T20:00:00Z");

        // Act
        List<Session> sessions = generate(group, from, FOUR_WEEKS, List.of());

        // Assert
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-01-12T20:00:00Z", "2026-01-19T20:00:00Z", "2026-01-26T20:00:00Z", "2026-02-02T20:00:00Z");
    }

    @Test
    void aSlotJustBeforeTheWindowStartIsLeftOut() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
        Instant from = Instant.parse("2026-01-12T20:00:01Z");

        // Act
        List<Session> sessions = generate(group, from, FOUR_WEEKS, List.of());

        // Assert
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-01-19T20:00:00Z", "2026-01-26T20:00:00Z", "2026-02-02T20:00:00Z", "2026-02-09T20:00:00Z");
    }

    @Test
    void honoursAnAssociationSpecificWindow() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));

        // Act
        List<Session> sessions = generate(group, WINTER_FROM, new SessionGenerationPolicy(2), List.of());

        // Assert
        assertThat(sessions).hasSize(2);
    }

    @Test
    void keepsEightPmLocalAcrossTheSpringForwardChange() {
        // Arrange - DST starts Sunday 29/03/2026; Mon+Sun 20:00 over 16/03 .. 13/04
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.MONDAY, 20, 0, 90), slot(DayOfWeek.SUNDAY, 20, 0, 90)));
        Instant from = Instant.parse("2026-03-16T00:00:00Z");

        // Act
        List<Session> sessions = generate(group, from, FOUR_WEEKS, List.of());

        // Assert - 20:00Z in WET, 19:00Z in WEST: always 20:00 on the Lisbon wall clock
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-03-16T20:00:00Z", "2026-03-22T20:00:00Z",
                "2026-03-23T20:00:00Z", "2026-03-29T19:00:00Z",
                "2026-03-30T19:00:00Z", "2026-04-05T19:00:00Z",
                "2026-04-06T19:00:00Z", "2026-04-12T19:00:00Z");
        assertThat(sessions).allSatisfy(s -> assertThat(lisbonTime(s)).isEqualTo(LocalTime.of(20, 0)));
    }

    @Test
    void keepsEightPmLocalAcrossTheFallBackChange() {
        // Arrange - DST ends Sunday 25/10/2026; Mon+Sun 20:00 over 12/10 .. 09/11
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.MONDAY, 20, 0, 90), slot(DayOfWeek.SUNDAY, 20, 0, 90)));
        Instant from = Instant.parse("2026-10-12T00:00:00Z");

        // Act
        List<Session> sessions = generate(group, from, FOUR_WEEKS, List.of());

        // Assert - 19:00Z in WEST, 20:00Z in WET
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-10-12T19:00:00Z", "2026-10-18T19:00:00Z",
                "2026-10-19T19:00:00Z", "2026-10-25T20:00:00Z",
                "2026-10-26T20:00:00Z", "2026-11-01T20:00:00Z",
                "2026-11-02T20:00:00Z", "2026-11-08T20:00:00Z");
        assertThat(sessions).allSatisfy(s -> assertThat(lisbonTime(s)).isEqualTo(LocalTime.of(20, 0)));
    }

    @Test
    void aSlotInTheSpringForwardGapIsShiftedForwardOnThatNightOnly() {
        // Arrange - 01:30 does not exist on 29/03/2026 (01:00 -> 02:00)
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60)));
        Instant from = Instant.parse("2026-03-16T00:00:00Z");

        // Act
        List<Session> sessions = generate(group, from, FOUR_WEEKS, List.of());

        // Assert
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-03-22T01:30:00Z", "2026-03-29T01:30:00Z", "2026-04-05T00:30:00Z", "2026-04-12T00:30:00Z");
        assertThat(sessions).extracting(SessionFactoryTest::lisbonTime).containsExactly(
                LocalTime.of(1, 30), LocalTime.of(2, 30), LocalTime.of(1, 30), LocalTime.of(1, 30));
    }

    @Test
    void aSlotInTheFallBackOverlapUsesTheEarlierOffset() {
        // Arrange - 01:30 happens twice on 25/10/2026
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60)));
        Instant from = Instant.parse("2026-10-12T00:00:00Z");

        // Act
        List<Session> sessions = generate(group, from, FOUR_WEEKS, List.of());

        // Assert - first 01:30 (WEST, 00:30Z) on 25/10, ordinary 01:30 afterwards
        assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-10-18T00:30:00Z", "2026-10-25T00:30:00Z", "2026-11-01T01:30:00Z", "2026-11-08T01:30:00Z");
    }

    @Test
    void rerunningAcrossADstChangeStillCreatesNothingNew() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60), slot(DayOfWeek.SUNDAY, 20, 0, 90)));
        Instant from = Instant.parse("2026-03-16T00:00:00Z");
        List<Session> first = generate(group, from, FOUR_WEEKS, List.of());

        // Act
        List<Session> second = generate(group, from, FOUR_WEEKS, first);

        // Assert
        assertThat(first).hasSize(8);
        assertThat(second).isEmpty();
    }

    @Test
    void sessionsAreIdentifiedByTheirStartInstantAsScheduled() {
        // Arrange
        TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));

        // Act
        List<Session> sessions = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());
        ScheduledOccurrence expected = MONDAY_8PM.occurrenceOn(LocalDate.of(2026, 1, 12));

        // Assert
        assertThat(sessions.get(0).startsAt()).isEqualTo(expected.startsAt());
        assertThat(sessions.get(0).endsAt()).isEqualTo(expected.endsAt());
    }
}
