package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.DuplicateLevelNameException;
import com.regivolley.api.domain.exception.InvalidAssociationException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssociationTest {

    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    private static Association association() {
        return Association.create("Porto Volleyball Club", "porto-volley", "123456789", "Porto",
                "info@portovolley.example", LEVELS);
    }

    private static List<String> names(Association association) {
        return association.levels().stream().map(Level::name).toList();
    }

    private static LevelId idOf(Association association, String name) {
        return association.levels().stream().filter(level -> level.name().equals(name)).findFirst().orElseThrow().id();
    }

    @Nested
    class Creation {

        @Test
        void createsAnAssociationWithDefaultPolicyAndOrderedLevels() {
            // Arrange
            // (default fixtures)

            // Act
            Association association = association();

            // Assert
            assertThat(association.id()).isNotNull();
            assertThat(association.name()).isEqualTo("Porto Volleyball Club");
            assertThat(association.shortName()).isEqualTo(ShortName.of("porto-volley"));
            assertThat(association.nif()).contains(Nif.of("123456789"));
            assertThat(association.locality()).isEqualTo("Porto");
            assertThat(association.contactEmail()).isEqualTo(EmailAddress.of("info@portovolley.example"));
            assertThat(association.bookingPolicy()).isEqualTo(BookingPolicy.defaults());
            assertThat(names(association)).containsExactlyElementsOf(LEVELS);
            assertThat(association.levels()).extracting(Level::rank).containsExactly(0, 1, 2);
            assertThat(association.levels()).allMatch(level -> level.associationId().equals(association.id()));
        }

        @Test
        void theFirstLevelIsTheEntryLevel() {
            // Arrange
            Association association = association();

            // Act
            Level entry = association.entryLevel();

            // Assert
            assertThat(entry.name()).isEqualTo("Beginner");
            assertThat(association.entryLevelId()).isEqualTo(entry.id());
        }

        @Test
        void aSingleLevelIsEnough() {
            // Arrange
            List<String> oneLevel = List.of("Open play");

            // Act
            Association association = Association.create("Club", "club", null, "Lisbon", "a@b.co", oneLevel);

            // Assert
            assertThat(association.levels()).hasSize(1);
            assertThat(association.entryLevel().name()).isEqualTo("Open play");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void theNifIsOptional(String nif) {
            // Arrange
            // (nif from the source)

            // Act
            Association association = Association.create("Club", "club", nif, "Lisbon", "a@b.co", LEVELS);

            // Assert
            assertThat(association.nif()).isEmpty();
        }

        @Test
        void trimsTheNameAndLocality() {
            // Arrange
            // (padded values)

            // Act
            Association association = Association.create("  Club  ", "club", null, "  Lisbon ", "a@b.co", LEVELS);

            // Assert
            assertThat(association.name()).isEqualTo("Club");
            assertThat(association.locality()).isEqualTo("Lisbon");
        }

        @Test
        void requiresAtLeastOneLevel() {
            // Arrange
            Executable act = () -> Association.create("Club", "club", null, "Lisbon", "a@b.co", List.of());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one level");
        }

        @Test
        void rejectsDuplicateLevelNamesIgnoringCase() {
            // Arrange
            Executable act = () -> Association.create("Club", "club", null, "Lisbon", "a@b.co",
                    List.of("Beginner", " beginner "));

            // Act
            DuplicateLevelNameException ex = assertThrows(DuplicateLevelNameException.class, act);

            // Assert
            assertThat(ex.name()).isEqualToIgnoringCase("beginner");
        }

        @Test
        void rejectsABlankLevelName() {
            // Arrange
            Executable act = () -> Association.create("Club", "club", null, "Lisbon", "a@b.co", List.of("Beginner", " "));

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("level name");
        }

        @Test
        void rejectsALevelNameThatIsTooLong() {
            // Arrange
            String tooLong = "x".repeat(Level.MAX_NAME_LENGTH + 1);
            Executable act = () -> Association.create("Club", "club", null, "Lisbon", "a@b.co", List.of(tooLong));

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("level name");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void requiresANameAndALocality(String blank) {
            // Arrange
            Executable noName = () -> Association.create(blank, "club", null, "Lisbon", "a@b.co", LEVELS);
            Executable noLocality = () -> Association.create("Club", "club", null, blank, "a@b.co", LEVELS);

            // Act
            InvalidFieldException name = assertThrows(InvalidFieldException.class, noName);
            InvalidFieldException locality = assertThrows(InvalidFieldException.class, noLocality);

            // Assert
            assertThat(name.field()).isEqualTo("association name");
            assertThat(locality.field()).isEqualTo("locality");
        }

        @Test
        void rejectsTooLongNameAndLocality() {
            // Arrange
            Executable longName = () -> Association.create("x".repeat(Association.MAX_NAME_LENGTH + 1), "club", null,
                    "Lisbon", "a@b.co", LEVELS);
            Executable longLocality = () -> Association.create("Club", "club", null,
                    "x".repeat(Association.MAX_LOCALITY_LENGTH + 1), "a@b.co", LEVELS);

            // Act
            InvalidFieldException name = assertThrows(InvalidFieldException.class, longName);
            InvalidFieldException locality = assertThrows(InvalidFieldException.class, longLocality);

            // Assert
            assertThat(name.field()).isEqualTo("association name");
            assertThat(locality.field()).isEqualTo("locality");
        }

        @Test
        void rejectsAnInvalidShortNameNifOrEmail() {
            // Arrange
            Executable badShortName = () -> Association.create("Club", "Bad Name", null, "Lisbon", "a@b.co", LEVELS);
            Executable badNif = () -> Association.create("Club", "club", "123456780", "Lisbon", "a@b.co", LEVELS);
            Executable badEmail = () -> Association.create("Club", "club", null, "Lisbon", "nope", LEVELS);

            // Act
            InvalidFieldException shortName = assertThrows(InvalidFieldException.class, badShortName);
            InvalidFieldException nif = assertThrows(InvalidFieldException.class, badNif);
            InvalidFieldException email = assertThrows(InvalidFieldException.class, badEmail);

            // Assert
            assertThat(shortName.field()).isEqualTo("short name");
            assertThat(nif.field()).isEqualTo("NIF");
            assertThat(email.field()).isEqualTo("email");
        }

        @Test
        void rejectsNullLevelNames() {
            // Arrange
            Executable act = () -> Association.create("Club", "club", null, "Lisbon", "a@b.co", null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("levelNames");
        }

        @Test
        void aTakenShortNameCarriesTheNameForTheUseCase() {
            // Arrange
            ShortName shortName = ShortName.of("porto-volley");

            // Act
            ShortNameAlreadyTakenException ex = new ShortNameAlreadyTakenException(shortName);

            // Assert
            assertThat(ex.shortName()).isEqualTo(shortName);
            assertThat(ex.getMessage()).contains("porto-volley");
        }
    }

    @Nested
    class Reconstruction {

        private final AssociationId id = AssociationId.generate();
        private final Level beginner = Level.reconstruct(LevelId.generate(), id, "Beginner", 0);
        private final Level advanced = Level.reconstruct(LevelId.generate(), id, "Advanced", 1);

        private Association rebuild(List<Level> levels, LevelId entry) {
            return Association.reconstruct(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), levels, entry);
        }

        @Test
        void acceptsLevelsInAnyOrderAndSortsThemByRank() {
            // Arrange
            List<Level> shuffled = List.of(advanced, beginner);

            // Act
            Association association = rebuild(shuffled, beginner.id());

            // Assert
            assertThat(association.levels()).containsExactly(beginner, advanced);
        }

        @Test
        void theEntryLevelNeedNotBeTheLowest() {
            // Arrange
            List<Level> levels = List.of(beginner, advanced);

            // Act
            Association association = rebuild(levels, advanced.id());

            // Assert
            assertThat(association.entryLevel()).isEqualTo(advanced);
        }

        @Test
        void rejectsAnEntryLevelThatIsNotOneOfTheLevels() {
            // Arrange
            Executable act = () -> rebuild(List.of(beginner, advanced), LevelId.generate());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("entry level");
        }

        @Test
        void rejectsNoLevels() {
            // Arrange
            Executable act = () -> rebuild(List.of(), LevelId.generate());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one level");
        }

        @Test
        void rejectsRankGapsAndRepeats() {
            // Arrange
            Level gap = Level.reconstruct(LevelId.generate(), id, "Gap", 5);
            Level repeat = Level.reconstruct(LevelId.generate(), id, "Repeat", 0);
            Executable withGap = () -> rebuild(List.of(beginner, gap), beginner.id());
            Executable withRepeat = () -> rebuild(List.of(beginner, repeat), beginner.id());

            // Act
            InvalidAssociationException gapEx = assertThrows(InvalidAssociationException.class, withGap);
            InvalidAssociationException repeatEx = assertThrows(InvalidAssociationException.class, withRepeat);

            // Assert
            assertThat(gapEx.getMessage()).contains("ranks");
            assertThat(repeatEx.getMessage()).contains("ranks");
        }

        @Test
        void rejectsALevelOfAnotherAssociation() {
            // Arrange
            Level foreign = Level.reconstruct(LevelId.generate(), AssociationId.generate(), "Foreign", 1);
            Executable act = () -> rebuild(List.of(beginner, foreign), beginner.id());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("another association");
        }

        @Test
        void rejectsTheSameLevelTwice() {
            // Arrange
            Level twin = Level.reconstruct(beginner.id(), id, "Twin", 1);
            Executable act = () -> rebuild(List.of(beginner, twin), beginner.id());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("twice");
        }

        @Test
        void rejectsANegativeLevelRank() {
            // Arrange
            Executable act = () -> Level.reconstruct(LevelId.generate(), id, "Bad", -1);

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("rank");
        }
    }

    @Nested
    class LevelManagement {

        @Test
        void addsALevelAsTheMostAdvanced() {
            // Arrange
            Association association = association();

            // Act
            Association updated = association.addLevel("Elite");

            // Assert
            assertThat(names(updated)).containsExactly("Beginner", "Intermediate", "Advanced", "Elite");
            assertThat(updated.levels()).extracting(Level::rank).containsExactly(0, 1, 2, 3);
            assertThat(updated.entryLevelId()).isEqualTo(association.entryLevelId());
            assertThat(names(association)).hasSize(3);
        }

        @Test
        void rejectsAddingADuplicateName() {
            // Arrange
            Association association = association();
            Executable act = () -> association.addLevel("ADVANCED");

            // Act
            DuplicateLevelNameException ex = assertThrows(DuplicateLevelNameException.class, act);

            // Assert
            assertThat(ex.name()).isEqualTo("ADVANCED");
        }

        @Test
        void renamesALevelKeepingItsIdRankAndEntryStatus() {
            // Arrange
            Association association = association();
            LevelId beginner = idOf(association, "Beginner");

            // Act
            Association updated = association.renameLevel(beginner, "  Newcomer ");

            // Assert
            assertThat(updated.level(beginner).name()).isEqualTo("Newcomer");
            assertThat(updated.level(beginner).rank()).isZero();
            assertThat(updated.entryLevelId()).isEqualTo(beginner);
        }

        @Test
        void renamingALevelToItsOwnNameIsAllowed() {
            // Arrange
            Association association = association();
            LevelId beginner = idOf(association, "Beginner");

            // Act
            Association updated = association.renameLevel(beginner, "Beginner");

            // Assert
            assertThat(names(updated)).containsExactlyElementsOf(LEVELS);
        }

        @Test
        void rejectsRenamingToAnotherLevelsName() {
            // Arrange
            Association association = association();
            LevelId beginner = idOf(association, "Beginner");
            Executable act = () -> association.renameLevel(beginner, "advanced");

            // Act
            DuplicateLevelNameException ex = assertThrows(DuplicateLevelNameException.class, act);

            // Assert
            assertThat(ex.name()).isEqualToIgnoringCase("advanced");
        }

        @Test
        void rejectsRenamingAnUnknownLevel() {
            // Arrange
            Association association = association();
            LevelId unknown = LevelId.generate();
            Executable act = () -> association.renameLevel(unknown, "Whatever");

            // Act
            LevelNotFoundException ex = assertThrows(LevelNotFoundException.class, act);

            // Assert
            assertThat(ex.levelId()).isEqualTo(unknown);
        }

        @Test
        void reordersLevelsAndRenumbersTheRanks() {
            // Arrange
            Association association = association();
            List<LevelId> reversed = new ArrayList<>(association.levels().stream().map(Level::id).toList());
            java.util.Collections.reverse(reversed);

            // Act
            Association updated = association.reorderLevels(reversed);

            // Assert
            assertThat(names(updated)).containsExactly("Advanced", "Intermediate", "Beginner");
            assertThat(updated.levels()).extracting(Level::rank).containsExactly(0, 1, 2);
            assertThat(updated.rankOf(idOf(updated, "Beginner")).rank()).isEqualTo(2);
        }

        @Test
        void reorderingKeepsTheEntryLevelEvenWhenItMoves() {
            // Arrange
            Association association = association();
            LevelId beginner = association.entryLevelId();
            List<LevelId> order = List.of(idOf(association, "Intermediate"), idOf(association, "Advanced"), beginner);

            // Act
            Association updated = association.reorderLevels(order);

            // Assert
            assertThat(updated.entryLevel().id()).isEqualTo(beginner);
            assertThat(updated.entryLevel().rank()).isEqualTo(2);
        }

        @Test
        void rejectsAReorderingThatIsNotAPermutation() {
            // Arrange
            Association association = association();
            LevelId beginner = idOf(association, "Beginner");
            Executable missing = () -> association.reorderLevels(List.of(beginner));
            Executable repeated = () -> association.reorderLevels(List.of(beginner, beginner, beginner));
            Executable foreign = () -> association.reorderLevels(
                    List.of(beginner, idOf(association, "Intermediate"), LevelId.generate()));

            // Act
            InvalidAssociationException missingEx = assertThrows(InvalidAssociationException.class, missing);
            InvalidAssociationException repeatedEx = assertThrows(InvalidAssociationException.class, repeated);
            InvalidAssociationException foreignEx = assertThrows(InvalidAssociationException.class, foreign);

            // Assert
            assertThat(missingEx.getMessage()).contains("every level exactly once");
            assertThat(repeatedEx.getMessage()).contains("every level exactly once");
            assertThat(foreignEx.getMessage()).contains("every level exactly once");
        }

        @Test
        void changesTheEntryLevelKeepingExactlyOne() {
            // Arrange
            Association association = association();
            LevelId intermediate = idOf(association, "Intermediate");

            // Act
            Association updated = association.changeEntryLevel(intermediate);

            // Assert
            assertThat(updated.entryLevel().name()).isEqualTo("Intermediate");
            assertThat(updated.entryLevelId()).isEqualTo(intermediate);
            assertThat(association.entryLevel().name()).isEqualTo("Beginner");
        }

        @Test
        void rejectsAnUnknownEntryLevel() {
            // Arrange
            Association association = association();
            Executable act = () -> association.changeEntryLevel(LevelId.generate());

            // Act
            LevelNotFoundException ex = assertThrows(LevelNotFoundException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("Level not found");
        }

        @Test
        void exposesTheLevelRankForEligibility() {
            // Arrange
            Association association = association();
            LevelId advanced = idOf(association, "Advanced");

            // Act
            LevelRank rank = association.rankOf(advanced);

            // Assert
            assertThat(rank).isEqualTo(new LevelRank(advanced, 2));
            assertThat(rank.isAtLeast(association.rankOf(association.entryLevelId()))).isTrue();
        }

        @Test
        void rejectsALevelThatIsNotTheAssociations() {
            // Arrange
            Association association = association();
            LevelId unknown = LevelId.generate();
            Executable act = () -> association.rankOf(unknown);

            // Act
            LevelNotFoundException ex = assertThrows(LevelNotFoundException.class, act);

            // Assert
            assertThat(ex.levelId()).isEqualTo(unknown);
        }
    }

    @Nested
    class Details {

        @Test
        void updatesTheDetailsKeepingTheShortNameLevelsAndPolicy() {
            // Arrange
            Association association = association();

            // Act
            Association updated = association.updateDetails("New Name", null, "Braga", "new@club.example");

            // Assert
            assertThat(updated.name()).isEqualTo("New Name");
            assertThat(updated.nif()).isEmpty();
            assertThat(updated.locality()).isEqualTo("Braga");
            assertThat(updated.contactEmail()).isEqualTo(EmailAddress.of("new@club.example"));
            assertThat(updated.shortName()).isEqualTo(association.shortName());
            assertThat(updated.id()).isEqualTo(association.id());
            assertThat(updated.levels()).isEqualTo(association.levels());
            assertThat(updated.bookingPolicy()).isEqualTo(association.bookingPolicy());
        }

        @Test
        void rejectsInvalidDetails() {
            // Arrange
            Association association = association();
            Executable act = () -> association.updateDetails("Name", "123456780", "Braga", "a@b.co");

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("NIF");
        }

        @Test
        void changesTheBookingPolicy() {
            // Arrange
            Association association = association();
            BookingPolicy stricter = new BookingPolicy(3, 12);

            // Act
            Association updated = association.changeBookingPolicy(stricter);

            // Assert
            assertThat(updated.bookingPolicy()).isEqualTo(stricter);
            assertThat(association.bookingPolicy()).isEqualTo(BookingPolicy.defaults());
        }

        @Test
        void isIdentifiedByItsIdAndPrintsOnlyIdentifiers() {
            // Arrange
            Association association = association();
            Association renamed = association.updateDetails("Other", null, "Braga", "x@y.co");

            // Act
            String text = association.toString();

            // Assert
            assertThat(association).isEqualTo(renamed).hasSameHashCodeAs(renamed);
            assertThat(association).isNotEqualTo(Association.create("Club", "club", null, "Lisbon", "a@b.co", LEVELS));
            assertThat(association).isNotEqualTo("not an association");
            assertThat(text).contains(association.id().toString()).doesNotContain("info@");
        }

        @Test
        void aLevelIsIdentifiedByItsId() {
            // Arrange
            Association association = association();
            Level level = association.entryLevel();
            Level renamed = level.renamedTo("Newcomer");

            // Act
            String text = level.toString();

            // Assert
            assertThat(level).isEqualTo(renamed).hasSameHashCodeAs(renamed);
            assertThat(level).isNotEqualTo(association.levels().get(1)).isNotEqualTo("not a level");
            assertThat(text).contains(level.id().toString());
        }
    }
}
