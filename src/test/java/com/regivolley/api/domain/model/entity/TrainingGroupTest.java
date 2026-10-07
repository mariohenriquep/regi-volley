package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.AtLeastOneAcceptedLevelRequiredException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.TrainingGroupArchivedException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ScheduledOccurrence;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainingGroupTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();
    private static final LevelId INTERMEDIATE = LevelId.generate();
    private static final LevelId ADVANCED = LevelId.generate();
    private static final VenueId VENUE = VenueId.generate();
    private static final MemberId COACH = MemberId.generate();
    private static final WeeklySlot MONDAY_8PM = slot(DayOfWeek.MONDAY, 20, 0, 90);
    private static final WeeklySlot WEDNESDAY_9PM = slot(DayOfWeek.WEDNESDAY, 21, 0, 90);
    private static final SessionGenerationPolicy FOUR_WEEKS = SessionGenerationPolicy.defaults();
    // Monday 12/01/2026 00:00Z (winter, Lisbon == UTC): window runs to Monday 09/02/2026
    private static final Instant WINTER_FROM = Instant.parse("2026-01-12T00:00:00Z");

    @Nested
    class Creation {

        @Test
        void createsAnActiveGroup() {
            // Arrange
            WeeklySchedule schedule = WeeklySchedule.of(MONDAY_8PM, WEDNESDAY_9PM);

            // Act
            TrainingGroup group = TrainingGroup.create(ASSOCIATION, "  Open play  ", Set.of(INTERMEDIATE, ADVANCED),
                    VENUE, schedule, 12, COACH);

            // Assert
            assertThat(group.id()).isNotNull();
            assertThat(group.associationId()).isEqualTo(ASSOCIATION);
            assertThat(group.name()).isEqualTo("Open play");
            assertThat(group.acceptedLevels()).containsExactlyInAnyOrder(INTERMEDIATE, ADVANCED);
            assertThat(group.venueId()).isEqualTo(VENUE);
            assertThat(group.schedule()).isEqualTo(schedule);
            assertThat(group.defaultCapacity()).isEqualTo(12);
            assertThat(group.coachId()).isEqualTo(COACH);
            assertThat(group.status()).isEqualTo(TrainingGroupStatus.ACTIVE);
            assertThat(group.isActive()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejectsAMissingName(String name) {
            // Arrange
            Executable act = () -> group(name, Set.of(INTERMEDIATE), 12);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("group name");
        }

        @Test
        void rejectsANameLongerThanTheLimit() {
            // Arrange
            Executable act = () -> group("x".repeat(TrainingGroup.MAX_NAME_LENGTH + 1), Set.of(INTERMEDIATE), 12);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("group name");
        }

        @Test
        void acceptsANameAtTheLimit() {
            // Arrange
            String name = "x".repeat(TrainingGroup.MAX_NAME_LENGTH);

            // Act
            TrainingGroup group = group(name, Set.of(INTERMEDIATE), 12);

            // Assert
            assertThat(group.name()).isEqualTo(name);
        }

        @Test
        void rejectsAGroupThatAcceptsNoLevel() {
            // Arrange
            Executable act = () -> group("Open play", Set.of(), 12);

            // Act
            AtLeastOneAcceptedLevelRequiredException ex = assertThrows(AtLeastOneAcceptedLevelRequiredException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one level");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void rejectsANonPositiveCapacity(int capacity) {
            // Arrange
            Executable act = () -> group("Open play", Set.of(INTERMEDIATE), capacity);

            // Act
            InvalidCapacityException ex = assertThrows(InvalidCapacityException.class, act);

            // Assert
            assertThat(ex.requestedCapacity()).isEqualTo(capacity);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullCollaborators")
        void rejectsNullCollaborators(String field, Executable act) {
            // Arrange
            // (act from the source)

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains(field);
        }

        static Stream<Arguments> nullCollaborators() {
            WeeklySchedule schedule = WeeklySchedule.of(MONDAY_8PM);
            Set<LevelId> levels = Set.of(INTERMEDIATE);
            return Stream.of(
                    Arguments.of("associationId", (Executable) () -> TrainingGroup.create(null, "A", levels, VENUE, schedule, 12, COACH)),
                    Arguments.of("acceptedLevels", (Executable) () -> TrainingGroup.create(ASSOCIATION, "A", null, VENUE, schedule, 12, COACH)),
                    Arguments.of("venueId", (Executable) () -> TrainingGroup.create(ASSOCIATION, "A", levels, null, schedule, 12, COACH)),
                    Arguments.of("schedule", (Executable) () -> TrainingGroup.create(ASSOCIATION, "A", levels, VENUE, null, 12, COACH)),
                    Arguments.of("coachId", (Executable) () -> TrainingGroup.create(ASSOCIATION, "A", levels, VENUE, schedule, 12, null)));
        }

        @Test
        void copiesTheAcceptedLevelsDefensively() {
            // Arrange
            Set<LevelId> levels = new HashSet<>(Set.of(INTERMEDIATE));
            TrainingGroup group = group("Open play", levels, 12);

            // Act
            levels.add(ADVANCED);

            // Assert
            assertThat(group.acceptedLevels()).containsExactly(INTERMEDIATE);
        }

        @Test
        void reconstructRebuildsAnArchivedGroupWithItsId() {
            // Arrange
            TrainingGroupId id = TrainingGroupId.generate();

            // Act
            TrainingGroup group = TrainingGroup.reconstruct(id, ASSOCIATION, "Old", Set.of(INTERMEDIATE), VENUE,
                    WeeklySchedule.of(MONDAY_8PM), 10, COACH, TrainingGroupStatus.ARCHIVED);

            // Assert
            assertThat(group.id()).isEqualTo(id);
            assertThat(group.status()).isEqualTo(TrainingGroupStatus.ARCHIVED);
            assertThat(group.isActive()).isFalse();
        }

        @Test
        void equalsAndHashCodeFollowTheId() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            TrainingGroup renamed = group.rename("Other");

            // Act
            boolean equal = renamed.equals(group);
            int renamedHash = renamed.hashCode();

            // Assert
            assertThat(equal).isTrue();
            assertThat(renamedHash).isEqualTo(group.hashCode());
        }

        @Test
        void groupsWithDifferentIdsAreNotEqual() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            TrainingGroup another = group("Open play", Set.of(INTERMEDIATE), 12);

            // Act
            boolean equal = group.equals(another);

            // Assert
            assertThat(equal).isFalse();
            assertThat(group).isNotEqualTo(null).isNotEqualTo("not a group");
        }

        @Test
        void toStringShowsTheIdAndStatusOnly() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);

            // Act
            String text = group.toString();

            // Assert
            assertThat(text).contains(group.id().toString()).contains("ACTIVE").doesNotContain("Open play");
        }
    }

    @Nested
    class Edits {

        @Test
        void renamesWithoutTouchingTheOriginal() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);

            // Act
            TrainingGroup renamed = group.rename("  Beginners  ");

            // Assert
            assertThat(renamed.name()).isEqualTo("Beginners");
            assertThat(group.name()).isEqualTo("Open play");
            assertThat(renamed.id()).isEqualTo(group.id());
        }

        @Test
        void rejectsABlankRename() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            Executable act = () -> group.rename(" ");

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("group name");
        }

        @Test
        void changesTheAcceptedLevels() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);

            // Act
            TrainingGroup changed = group.changeAcceptedLevels(Set.of(INTERMEDIATE, ADVANCED));

            // Assert
            assertThat(changed.acceptedLevels()).containsExactlyInAnyOrder(INTERMEDIATE, ADVANCED);
            assertThat(group.acceptedLevels()).containsExactly(INTERMEDIATE);
        }

        @Test
        void rejectsChangingTheAcceptedLevelsToNone() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            Executable act = () -> group.changeAcceptedLevels(Set.of());

            // Act
            AtLeastOneAcceptedLevelRequiredException ex = assertThrows(AtLeastOneAcceptedLevelRequiredException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one level");
        }

        @Test
        void changesTheCapacity() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);

            // Act
            TrainingGroup changed = group.changeCapacity(18);

            // Assert
            assertThat(changed.defaultCapacity()).isEqualTo(18);
            assertThat(group.defaultCapacity()).isEqualTo(12);
        }

        @Test
        void rejectsChangingTheCapacityToZero() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            Executable act = () -> group.changeCapacity(0);

            // Act
            InvalidCapacityException ex = assertThrows(InvalidCapacityException.class, act);

            // Assert
            assertThat(ex.requestedCapacity()).isZero();
        }

        @Test
        void changesTheCoach() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            MemberId newCoach = MemberId.generate();

            // Act
            TrainingGroup changed = group.changeCoach(newCoach);

            // Assert
            assertThat(changed.coachId()).isEqualTo(newCoach);
            assertThat(group.coachId()).isEqualTo(COACH);
        }

        @Test
        void changesTheSchedule() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            WeeklySchedule newSchedule = WeeklySchedule.of(WEDNESDAY_9PM);

            // Act
            TrainingGroup changed = group.changeSchedule(newSchedule);

            // Assert
            assertThat(changed.schedule()).isEqualTo(newSchedule);
            assertThat(group.schedule().slots()).containsExactly(MONDAY_8PM);
        }

        @Test
        void editsOnlyAffectSessionsGeneratedAfterwards() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);
            List<Session> before = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());
            TrainingGroup edited = group.changeCapacity(20).changeCoach(MemberId.generate());

            // Act
            List<Session> after = edited.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());

            // Assert - sessions already produced keep the values they were generated with
            assertThat(before).allSatisfy(s -> assertThat(s.capacity()).isEqualTo(12));
            assertThat(before).allSatisfy(s -> assertThat(s.coachId()).isEqualTo(COACH));
            assertThat(after).allSatisfy(s -> assertThat(s.capacity()).isEqualTo(20));
            assertThat(after).allSatisfy(s -> assertThat(s.coachId()).isEqualTo(edited.coachId()).isNotEqualTo(COACH));
        }

        @Test
        void archivesTheGroup() {
            // Arrange
            TrainingGroup group = group("Open play", Set.of(INTERMEDIATE), 12);

            // Act
            TrainingGroup archived = group.archive();

            // Assert
            assertThat(archived.status()).isEqualTo(TrainingGroupStatus.ARCHIVED);
            assertThat(archived.isActive()).isFalse();
            assertThat(group.isActive()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("editsOnAnArchivedGroup")
        void rejectsEveryEditOnAnArchivedGroup(String edit, Function<TrainingGroup, TrainingGroup> change) {
            // Arrange
            TrainingGroup archived = group("Open play", Set.of(INTERMEDIATE), 12).archive();
            Executable act = () -> change.apply(archived);

            // Act
            TrainingGroupArchivedException ex = assertThrows(TrainingGroupArchivedException.class, act);

            // Assert
            assertThat(ex.trainingGroupId()).isEqualTo(archived.id());
        }

        static Stream<Arguments> editsOnAnArchivedGroup() {
            return Stream.of(
                    Arguments.of("rename", (Function<TrainingGroup, TrainingGroup>) g -> g.rename("New")),
                    Arguments.of("changeAcceptedLevels", (Function<TrainingGroup, TrainingGroup>) g -> g.changeAcceptedLevels(Set.of(ADVANCED))),
                    Arguments.of("changeCapacity", (Function<TrainingGroup, TrainingGroup>) g -> g.changeCapacity(10)),
                    Arguments.of("changeCoach", (Function<TrainingGroup, TrainingGroup>) g -> g.changeCoach(MemberId.generate())),
                    Arguments.of("changeSchedule", (Function<TrainingGroup, TrainingGroup>) g -> g.changeSchedule(WeeklySchedule.of(WEDNESDAY_9PM))),
                    Arguments.of("archive", (Function<TrainingGroup, TrainingGroup>) TrainingGroup::archive));
        }
    }

    @Nested
    class SessionGeneration {

        @Test
        void generatesEveryOccurrenceInTheWindowForAMultiSlotSchedule() {
            // Arrange - Mon+Wed 20:00 / 21:00 over 12/01 .. 09/02/2026 (exclusive)
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM, WEDNESDAY_9PM));

            // Act
            List<Session> sessions = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());

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
            List<Session> sessions = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());

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
            List<Session> first = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());

            // Act
            List<Session> second = group.generateSessions(WINTER_FROM, FOUR_WEEKS, first);

            // Assert
            assertThat(second).isEmpty();
        }

        @Test
        void generatesOnlyTheMissingSessionsWhenSomeAlreadyExist() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
            List<Session> all = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());
            List<Session> existing = List.of(all.get(0), all.get(2));

            // Act
            List<Session> generated = group.generateSessions(WINTER_FROM, FOUR_WEEKS, existing);

            // Assert
            assertThat(generated).extracting(Session::startsAt)
                    .containsExactly(all.get(1).startsAt(), all.get(3).startsAt());
        }

        @Test
        void generatesOnlyTheNewWeeksWhenTheWindowAdvances() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
            List<Session> existing = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());
            Instant aWeekLater = WINTER_FROM.plus(Duration.ofDays(7));

            // Act
            List<Session> generated = group.generateSessions(aWeekLater, FOUR_WEEKS, existing);

            // Assert
            assertThat(generated).extracting(s -> s.startsAt().toString()).containsExactly("2026-02-09T20:00:00Z");
        }

        @Test
        void aCancelledSessionIsNotGeneratedAgain() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
            List<Session> all = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());
            Session cancelled = all.get(0).cancel("Venue closed");

            // Act
            List<Session> generated = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of(cancelled));

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
            Session otherGroup = Session.create(ASSOCIATION, TrainingGroupId.generate(), COACH, start,
                    start.plus(Duration.ofMinutes(90)), 12);

            // Act
            List<Session> generated = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of(otherGroup));

            // Assert
            assertThat(generated).hasSize(4);
        }

        @Test
        void rejectsExistingSessionsOfAnotherAssociation() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
            Instant start = Instant.parse("2026-01-12T20:00:00Z");
            Session foreign = Session.create(AssociationId.generate(), group.id(), COACH, start,
                    start.plus(Duration.ofMinutes(90)), 12);
            Executable act = () -> group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of(foreign));

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
            List<Session> sessions = group.generateSessions(from, new SessionGenerationPolicy(2), List.of());

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
            List<Session> first = group.generateSessions(from, new SessionGenerationPolicy(2), List.of());

            // Act
            List<Session> second = group.generateSessions(from, new SessionGenerationPolicy(2), first);

            // Assert
            assertThat(second).isEmpty();
        }

        @Test
        void anArchivedGroupGeneratesNothing() {
            // Arrange
            TrainingGroup archived = group(WeeklySchedule.of(MONDAY_8PM)).archive();

            // Act
            List<Session> generated = archived.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());

            // Assert
            assertThat(generated).isEmpty();
        }

        @Test
        void includesASlotExactlyAtTheWindowStartAndExcludesOneExactlyAtItsEnd() {
            // Arrange - from = Monday 20:00 itself; the window ends Monday 09/02 20:00 exactly
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
            Instant from = Instant.parse("2026-01-12T20:00:00Z");

            // Act
            List<Session> sessions = group.generateSessions(from, FOUR_WEEKS, List.of());

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
            List<Session> sessions = group.generateSessions(from, FOUR_WEEKS, List.of());

            // Assert
            assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                    "2026-01-19T20:00:00Z", "2026-01-26T20:00:00Z", "2026-02-02T20:00:00Z", "2026-02-09T20:00:00Z");
        }

        @Test
        void honoursAnAssociationSpecificWindow() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));

            // Act
            List<Session> sessions = group.generateSessions(WINTER_FROM, new SessionGenerationPolicy(2), List.of());

            // Assert
            assertThat(sessions).hasSize(2);
        }

        @Test
        void keepsEightPmLocalAcrossTheSpringForwardChange() {
            // Arrange - DST starts Sunday 29/03/2026; Mon+Sun 20:00 over 16/03 .. 13/04
            TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.MONDAY, 20, 0, 90), slot(DayOfWeek.SUNDAY, 20, 0, 90)));
            Instant from = Instant.parse("2026-03-16T00:00:00Z");

            // Act
            List<Session> sessions = group.generateSessions(from, FOUR_WEEKS, List.of());

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
            List<Session> sessions = group.generateSessions(from, FOUR_WEEKS, List.of());

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
            List<Session> sessions = group.generateSessions(from, FOUR_WEEKS, List.of());

            // Assert
            assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                    "2026-03-22T01:30:00Z", "2026-03-29T01:30:00Z", "2026-04-05T00:30:00Z", "2026-04-12T00:30:00Z");
            assertThat(sessions).extracting(TrainingGroupTest::lisbonTime).containsExactly(
                    LocalTime.of(1, 30), LocalTime.of(2, 30), LocalTime.of(1, 30), LocalTime.of(1, 30));
        }

        @Test
        void aSlotInTheFallBackOverlapUsesTheEarlierOffset() {
            // Arrange - 01:30 happens twice on 25/10/2026
            TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60)));
            Instant from = Instant.parse("2026-10-12T00:00:00Z");

            // Act
            List<Session> sessions = group.generateSessions(from, FOUR_WEEKS, List.of());

            // Assert - first 01:30 (WEST, 00:30Z) on 25/10, ordinary 01:30 afterwards
            assertThat(sessions).extracting(s -> s.startsAt().toString()).containsExactly(
                    "2026-10-18T00:30:00Z", "2026-10-25T00:30:00Z", "2026-11-01T01:30:00Z", "2026-11-08T01:30:00Z");
        }

        @Test
        void rerunningAcrossADstChangeStillCreatesNothingNew() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 1, 30, 60), slot(DayOfWeek.SUNDAY, 20, 0, 90)));
            Instant from = Instant.parse("2026-03-16T00:00:00Z");
            List<Session> first = group.generateSessions(from, FOUR_WEEKS, List.of());

            // Act
            List<Session> second = group.generateSessions(from, FOUR_WEEKS, first);

            // Assert
            assertThat(first).hasSize(8);
            assertThat(second).isEmpty();
        }

        @Test
        void sessionsAreIdentifiedByTheirStartInstantAsScheduled() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));

            // Act
            List<Session> sessions = group.generateSessions(WINTER_FROM, FOUR_WEEKS, List.of());
            ScheduledOccurrence expected = MONDAY_8PM.occurrenceOn(LocalDate.of(2026, 1, 12));

            // Assert
            assertThat(sessions.get(0).startsAt()).isEqualTo(expected.startsAt());
            assertThat(sessions.get(0).endsAt()).isEqualTo(expected.endsAt());
        }
    }

    private static LocalTime lisbonTime(Session session) {
        return session.startsAt().atZone(ZoneId.of("Europe/Lisbon")).toLocalTime();
    }

    private static TrainingGroup group(WeeklySchedule schedule) {
        return TrainingGroup.create(ASSOCIATION, "Open play", Set.of(INTERMEDIATE), VENUE, schedule, 12, COACH);
    }

    private static TrainingGroup group(String name, Set<LevelId> levels, int capacity) {
        return TrainingGroup.create(ASSOCIATION, name, levels, VENUE, WeeklySchedule.of(MONDAY_8PM), capacity, COACH);
    }

    private static WeeklySlot slot(DayOfWeek day, int hour, int minute, long minutes) {
        return new WeeklySlot(day, LocalTime.of(hour, minute), Duration.ofMinutes(minutes));
    }
}
