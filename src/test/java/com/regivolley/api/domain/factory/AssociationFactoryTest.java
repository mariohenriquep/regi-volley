package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.AtLeastOneLevelRequiredException;
import com.regivolley.api.domain.exception.DuplicateLevelNameException;
import com.regivolley.api.domain.exception.InvalidAssociationException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Nif;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.ShortName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssociationFactoryTest {

    private static final List<String> LEVELS = List.of("Beginner", "Intermediate", "Advanced");

    private static Association association() {
        return AssociationFactory.create("Porto Volleyball Club", "porto-volley", "123456789", "Porto",
                "info@portovolley.example", LEVELS);
    }

    private static List<String> names(Association association) {
        return association.levels().stream().map(Level::name).toList();
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
            assertThat(association.sessionGenerationPolicy()).isEqualTo(SessionGenerationPolicy.defaults());
            assertThat(association.sessionGenerationPolicy().windowWeeks()).isEqualTo(4);
            assertThat(association.version()).isZero();
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
            Association association = AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co", oneLevel);

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
            Association association = AssociationFactory.create("Club", "club", nif, "Lisbon", "a@b.co", LEVELS);

            // Assert
            assertThat(association.nif()).isEmpty();
        }

        @Test
        void trimsTheNameAndLocality() {
            // Arrange
            // (padded values)

            // Act
            Association association = AssociationFactory.create("  Club  ", "club", null, "  Lisbon ", "a@b.co", LEVELS);

            // Assert
            assertThat(association.name()).isEqualTo("Club");
            assertThat(association.locality()).isEqualTo("Lisbon");
        }

        @Test
        void requiresAtLeastOneLevel() {
            // Arrange
            Executable act = () -> AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co", List.of());

            // Act
            AtLeastOneLevelRequiredException ex = assertThrows(AtLeastOneLevelRequiredException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one level");
        }

        @Test
        void acceptsNamesAndLocalityOfTheMaximumLength() {
            // Arrange
            String name = "n".repeat(Association.MAX_NAME_LENGTH);
            String locality = "l".repeat(Association.MAX_LOCALITY_LENGTH);
            String levelName = "v".repeat(Level.MAX_NAME_LENGTH);

            // Act
            Association association = AssociationFactory.create(name, "club", null, locality, "a@b.co", List.of(levelName));

            // Assert
            assertThat(association.name()).hasSize(Association.MAX_NAME_LENGTH);
            assertThat(association.locality()).hasSize(Association.MAX_LOCALITY_LENGTH);
            assertThat(association.entryLevel().name()).hasSize(Level.MAX_NAME_LENGTH);
        }

        @Test
        void rejectsDuplicateLevelNamesIgnoringCase() {
            // Arrange
            Executable act = () -> AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co",
                    List.of("Beginner", " beginner "));

            // Act
            DuplicateLevelNameException ex = assertThrows(DuplicateLevelNameException.class, act);

            // Assert
            assertThat(ex.name()).isEqualToIgnoringCase("beginner");
        }

        @Test
        void rejectsABlankLevelName() {
            // Arrange
            Executable act = () -> AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co", List.of("Beginner", " "));

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("level name");
        }

        @Test
        void rejectsALevelNameThatIsTooLong() {
            // Arrange
            String tooLong = "x".repeat(Level.MAX_NAME_LENGTH + 1);
            Executable act = () -> AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co", List.of(tooLong));

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
            Executable noName = () -> AssociationFactory.create(blank, "club", null, "Lisbon", "a@b.co", LEVELS);
            Executable noLocality = () -> AssociationFactory.create("Club", "club", null, blank, "a@b.co", LEVELS);

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
            Executable longName = () -> AssociationFactory.create("x".repeat(Association.MAX_NAME_LENGTH + 1), "club", null,
                    "Lisbon", "a@b.co", LEVELS);
            Executable longLocality = () -> AssociationFactory.create("Club", "club", null,
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
            Executable badShortName = () -> AssociationFactory.create("Club", "Bad Name", null, "Lisbon", "a@b.co", LEVELS);
            Executable badNif = () -> AssociationFactory.create("Club", "club", "123456780", "Lisbon", "a@b.co", LEVELS);
            Executable badEmail = () -> AssociationFactory.create("Club", "club", null, "Lisbon", "nope", LEVELS);

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
            Executable act = () -> AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co", null);

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
        private final Level beginner = AssociationFactory.reconstituteLevel(LevelId.generate(), id, "Beginner", 0);
        private final Level advanced = AssociationFactory.reconstituteLevel(LevelId.generate(), id, "Advanced", 1);

        private Association rebuild(List<Level> levels, LevelId entry) {
            return AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), SessionGenerationPolicy.defaults(), NoShowPolicy.defaults(),
                    levels, entry, 0L);
        }

        @Test
        void keepsTheSessionGenerationPolicyAndVersionItWasGiven() {
            // Arrange
            SessionGenerationPolicy sixWeeks = new SessionGenerationPolicy(6);

            // Act
            Association association = AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), sixWeeks, NoShowPolicy.defaults(), List.of(beginner), beginner.id(), 7L);

            // Assert
            assertThat(association.sessionGenerationPolicy()).isEqualTo(sixWeeks);
            assertThat(association.version()).isEqualTo(7L);
        }

        @Test
        void editsKeepTheSessionGenerationPolicyAndVersion() {
            // Arrange
            SessionGenerationPolicy sixWeeks = new SessionGenerationPolicy(6);
            Association association = AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), sixWeeks, NoShowPolicy.defaults(), List.of(beginner), beginner.id(), 7L);

            // Act
            Association edited = association.updateDetails("New name", null, "Porto", "x@y.co")
                    .changeBookingPolicy(new BookingPolicy(3, 2))
                    .addLevel("Advanced");

            // Assert
            assertThat(edited.sessionGenerationPolicy()).isEqualTo(sixWeeks);
            assertThat(edited.version()).isEqualTo(7L);
        }

        @Test
        void startsWithTheDefaultNoShowPolicyAndKeepsTheOneItWasGiven() {
            // Arrange
            NoShowPolicy five = new NoShowPolicy(5);

            // Act
            Association created = association();
            Association custom = AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), SessionGenerationPolicy.defaults(), five,
                    List.of(beginner), beginner.id(), 2L);

            // Assert
            assertThat(created.noShowPolicy()).isEqualTo(NoShowPolicy.defaults());
            assertThat(custom.noShowPolicy()).isEqualTo(five);
        }

        @Test
        void changesTheNoShowPolicyAndEveryOtherEditKeepsIt() {
            // Arrange
            Association association = association();
            NoShowPolicy two = new NoShowPolicy(2);

            // Act
            Association changed = association.changeNoShowPolicy(two);
            Association edited = changed.changeBookingPolicy(new BookingPolicy(3, 2)).addLevel("Pro");

            // Assert
            assertThat(changed.noShowPolicy()).isEqualTo(two);
            assertThat(edited.noShowPolicy()).isEqualTo(two);
            assertThat(association.noShowPolicy()).isEqualTo(NoShowPolicy.defaults());
        }

        @Test
        void rejectsAMissingNoShowPolicy() {
            // Arrange
            Executable missing = () -> AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), SessionGenerationPolicy.defaults(), null,
                    List.of(beginner), beginner.id(), 0L);

            // Act
            NullPointerException npe = assertThrows(NullPointerException.class, missing);

            // Assert
            assertThat(npe.getMessage()).contains("noShowPolicy");
        }

        @Test
        void rejectsANegativeVersionOrAMissingGenerationPolicy() {
            // Arrange
            Executable negative = () -> AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), SessionGenerationPolicy.defaults(), NoShowPolicy.defaults(),
                    List.of(beginner), beginner.id(), -1L);
            Executable missing = () -> AssociationFactory.reconstitute(id, "Club", ShortName.of("club"), null, "Lisbon",
                    EmailAddress.of("a@b.co"), BookingPolicy.defaults(), null, NoShowPolicy.defaults(), List.of(beginner), beginner.id(), 0L);

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, negative);
            NullPointerException npe = assertThrows(NullPointerException.class, missing);

            // Assert
            assertThat(ex.getMessage()).contains("version");
            assertThat(npe.getMessage()).contains("sessionGenerationPolicy");
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
            Level gap = AssociationFactory.reconstituteLevel(LevelId.generate(), id, "Gap", 5);
            Level repeat = AssociationFactory.reconstituteLevel(LevelId.generate(), id, "Repeat", 0);
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
            Level foreign = AssociationFactory.reconstituteLevel(LevelId.generate(), AssociationId.generate(), "Foreign", 1);
            Executable act = () -> rebuild(List.of(beginner, foreign), beginner.id());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("another association");
        }

        @Test
        void rejectsTheSameLevelTwice() {
            // Arrange
            Level twin = AssociationFactory.reconstituteLevel(beginner.id(), id, "Twin", 1);
            Executable act = () -> rebuild(List.of(beginner, twin), beginner.id());

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("twice");
        }

        @Test
        void rejectsANegativeLevelRank() {
            // Arrange
            Executable act = () -> AssociationFactory.reconstituteLevel(LevelId.generate(), id, "Bad", -1);

            // Act
            InvalidAssociationException ex = assertThrows(InvalidAssociationException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("rank");
        }
    }
}
