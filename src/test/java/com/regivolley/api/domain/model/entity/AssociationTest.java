package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.DuplicateLevelNameException;
import com.regivolley.api.domain.exception.InvalidAssociationException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.LevelRank;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssociationTest {
    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    private static Association association() {
        return AssociationFactory.create("Porto Volleyball Club", "porto-volley", "123456789", "Porto",
                "info@portovolley.example", LEVELS);
    }

    private static List<String> names(Association association) {
        return association.levels().stream().map(Level::name).toList();
    }

    private static LevelId idOf(Association association, String name) {
        return association.levels().stream().filter(level -> level.name().equals(name)).findFirst().orElseThrow().id();
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
            InvalidFieldException missingEx = assertThrows(InvalidFieldException.class, missing);
            InvalidFieldException repeatedEx = assertThrows(InvalidFieldException.class, repeated);
            InvalidFieldException foreignEx = assertThrows(InvalidFieldException.class, foreign);

            // Assert
            assertThat(missingEx.getMessage()).contains("every level exactly once");
            assertThat(repeatedEx.getMessage()).contains("every level exactly once");
            assertThat(foreignEx.getMessage()).contains("every level exactly once");
            assertThat(foreignEx.field()).isEqualTo("levelIds");
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
        void exposesTheRanksOfSeveralLevelsAtOnce() {
            // Arrange
            Association association = association();
            LevelId beginner = idOf(association, "Beginner");
            LevelId advanced = idOf(association, "Advanced");

            // Act
            Set<LevelRank> ranks = association.ranksOf(Set.of(beginner, advanced));

            // Assert
            assertThat(ranks).containsExactlyInAnyOrder(new LevelRank(beginner, 0), new LevelRank(advanced, 2));
        }

        @Test
        void ranksOfRejectsAnyLevelThatIsNotTheAssociations() {
            // Arrange
            Association association = association();
            Executable act = () -> association.ranksOf(Set.of(idOf(association, "Beginner"), LevelId.generate()));

            // Act
            LevelNotFoundException ex = assertThrows(LevelNotFoundException.class, act);

            // Assert
            assertThat(ex).isNotNull();
        }

        @Test
        void knowsWhetherItHasAllTheGivenLevels() {
            // Arrange
            Association association = association();
            Set<LevelId> own = Set.of(idOf(association, "Beginner"), idOf(association, "Advanced"));
            Set<LevelId> withForeign = Set.of(idOf(association, "Beginner"), LevelId.generate());

            // Act
            boolean ownLevels = association.hasAllLevels(own);
            boolean foreign = association.hasAllLevels(withForeign);

            // Assert
            assertThat(ownLevels).isTrue();
            assertThat(foreign).isFalse();
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
            assertThat(association).isNotEqualTo(AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co", LEVELS));
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

    @Test
    void requireLevelsAcceptsTheAssociationsOwnLevelsAndNamesTheFirstForeignOne() {
        // Arrange
        Association association = association();
        Set<LevelId> own = Set.of(idOf(association, "Beginner"), idOf(association, "Advanced"));
        LevelId foreign = LevelId.generate();
        Executable act = () -> association.requireLevels(Set.of(idOf(association, "Beginner"), foreign));

        // Act
        association.requireLevels(own);
        association.requireLevels(Set.of());
        LevelNotFoundException ex = assertThrows(LevelNotFoundException.class, act);

        // Assert
        assertThat(ex.levelId()).isEqualTo(foreign);
    }
}
