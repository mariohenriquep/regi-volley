package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.AtLeastOneAcceptedLevelRequiredException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.TrainingGroupArchivedException;
import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.factory.TrainingGroupFactory;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ScheduledOccurrence;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
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
    class VersionAndIdentity {
        @Test
        void editsKeepTheVersionTheGroupWasLoadedWith() {
            // Arrange
            TrainingGroup loaded = TrainingGroupFactory.reconstitute(TrainingGroupId.generate(), ASSOCIATION, "Old",
                    Set.of(INTERMEDIATE), VENUE, WeeklySchedule.of(MONDAY_8PM), 10, COACH, TrainingGroupStatus.ACTIVE, 3L);

            // Act
            TrainingGroup edited = loaded.rename("New").changeCapacity(8).changeSchedule(WeeklySchedule.of(WEDNESDAY_9PM))
                    .changeAcceptedLevels(Set.of(ADVANCED)).changeCoach(MemberId.generate());
            TrainingGroup archived = edited.archive();

            // Assert
            assertThat(edited.version()).isEqualTo(3L);
            assertThat(archived.version()).isEqualTo(3L);
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
            List<Session> before = generate(group, WINTER_FROM, FOUR_WEEKS, List.of());
            TrainingGroup edited = group.changeCapacity(20).changeCoach(MemberId.generate());

            // Act
            List<Session> after = generate(edited, WINTER_FROM, FOUR_WEEKS, List.of());

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
    class OccurrencesToGenerate {
        @Test
        void theGroupSaysWhichOccurrencesStillNeedASession() {
            // Arrange
            TrainingGroup group = group(WeeklySchedule.of(MONDAY_8PM));
            Session january12 = SessionFactory.create(ASSOCIATION, group.id(), COACH,
                    Instant.parse("2026-01-12T20:00:00Z"), Instant.parse("2026-01-12T21:30:00Z"), 12);

            // Act
            List<ScheduledOccurrence> missing = group.occurrencesToGenerate(WINTER_FROM, FOUR_WEEKS, List.of(january12));

            // Assert - the three later Mondays, oldest first, as occurrences: no session is built by the group
            assertThat(missing).extracting(o -> o.startsAt().toString()).containsExactly(
                    "2026-01-19T20:00:00Z", "2026-01-26T20:00:00Z", "2026-02-02T20:00:00Z");
            assertThat(missing).allSatisfy(o -> assertThat(Duration.between(o.startsAt(), o.endsAt()))
                    .isEqualTo(Duration.ofMinutes(90)));
        }

        @Test
        void anArchivedGroupHasNoOccurrencesToGenerate() {
            // Arrange
            TrainingGroup archived = group(WeeklySchedule.of(MONDAY_8PM)).archive();

            // Act
            List<ScheduledOccurrence> missing = archived.occurrencesToGenerate(WINTER_FROM, FOUR_WEEKS, List.of());

            // Assert
            assertThat(missing).isEmpty();
        }
    }

    /** What the use case does: the group says which occurrences are missing, the factory builds their sessions. */
    private static List<Session> generate(TrainingGroup group, Instant from, SessionGenerationPolicy policy,
                                          List<Session> existing) {
        return SessionFactory.createSessionsFor(group, from, policy, existing);
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
}
