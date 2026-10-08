package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.AtLeastOneAcceptedLevelRequiredException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidTrainingGroupException;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainingGroupFactoryTest {
    private static final AssociationId ASSOCIATION = AssociationId.generate();

    private static final LevelId INTERMEDIATE = LevelId.generate();

    private static final LevelId ADVANCED = LevelId.generate();

    private static final VenueId VENUE = VenueId.generate();

    private static final MemberId COACH = MemberId.generate();

    private static final WeeklySlot MONDAY_8PM = slot(DayOfWeek.MONDAY, 20, 0, 90);

    private static final WeeklySlot WEDNESDAY_9PM = slot(DayOfWeek.WEDNESDAY, 21, 0, 90);

    private static TrainingGroup group(WeeklySchedule schedule) {
        return TrainingGroupFactory.create(ASSOCIATION, "Open play", Set.of(INTERMEDIATE), VENUE, schedule, 12, COACH);
    }

    private static TrainingGroup group(String name, Set<LevelId> levels, int capacity) {
        return TrainingGroupFactory.create(ASSOCIATION, name, levels, VENUE, WeeklySchedule.of(MONDAY_8PM), capacity, COACH);
    }

    private static WeeklySlot slot(DayOfWeek day, int hour, int minute, long minutes) {
        return new WeeklySlot(day, LocalTime.of(hour, minute), Duration.ofMinutes(minutes));
    }

    @Test
    void createsAnActiveGroup() {
        // Arrange
        WeeklySchedule schedule = WeeklySchedule.of(MONDAY_8PM, WEDNESDAY_9PM);

        // Act
        TrainingGroup group = TrainingGroupFactory.create(ASSOCIATION, "  Open play  ", Set.of(INTERMEDIATE, ADVANCED),
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
        assertThat(group.version()).isZero();
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
                Arguments.of("associationId", (Executable) () -> TrainingGroupFactory.create(null, "A", levels, VENUE, schedule, 12, COACH)),
                Arguments.of("acceptedLevels", (Executable) () -> TrainingGroupFactory.create(ASSOCIATION, "A", null, VENUE, schedule, 12, COACH)),
                Arguments.of("venueId", (Executable) () -> TrainingGroupFactory.create(ASSOCIATION, "A", levels, null, schedule, 12, COACH)),
                Arguments.of("schedule", (Executable) () -> TrainingGroupFactory.create(ASSOCIATION, "A", levels, VENUE, null, 12, COACH)),
                Arguments.of("coachId", (Executable) () -> TrainingGroupFactory.create(ASSOCIATION, "A", levels, VENUE, schedule, 12, null)));
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
        TrainingGroup group = TrainingGroupFactory.reconstitute(id, ASSOCIATION, "Old", Set.of(INTERMEDIATE), VENUE,
                WeeklySchedule.of(MONDAY_8PM), 10, COACH, TrainingGroupStatus.ARCHIVED, 5L);

        // Assert
        assertThat(group.id()).isEqualTo(id);
        assertThat(group.version()).isEqualTo(5L);
        assertThat(group.status()).isEqualTo(TrainingGroupStatus.ARCHIVED);
        assertThat(group.isActive()).isFalse();
    }

    @Test
    void rejectsANegativeVersion() {
        // Arrange
        Executable act = () -> TrainingGroupFactory.reconstitute(TrainingGroupId.generate(), ASSOCIATION, "Old",
                Set.of(INTERMEDIATE), VENUE, WeeklySchedule.of(MONDAY_8PM), 10, COACH, TrainingGroupStatus.ACTIVE, -1L);

        // Act
        InvalidTrainingGroupException ex = assertThrows(InvalidTrainingGroupException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("version");
    }
}
