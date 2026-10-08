package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidPlanException;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanFactoryTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();

    private static final Money PRICE = Money.ofCents(3500);

    private static final LevelId OPEN_PLAY = LevelId.generate();

    private static final LevelId ADVANCED = LevelId.generate();

    @Nested
    class Versioning {

        @Test
        void aNewPlanStartsAtVersionZeroAndReconstructKeepsTheGivenOne() {
            // Arrange
            PlanTerms terms = PlanTerms.monthlyUnlimited(Set.of());

            // Act
            Plan created = PlanFactory.create(ASSOCIATION, "Plan", terms, PRICE, null);
            Plan loaded = PlanFactory.reconstitute(created.id(), ASSOCIATION, "Plan", terms, PRICE, null, 3L);

            // Assert
            assertThat(created.version()).isZero();
            assertThat(loaded.version()).isEqualTo(3L);
        }

        @Test
        void rejectsANegativeVersion() {
            // Arrange
            Executable act = () -> PlanFactory.reconstitute(PlanId.generate(), ASSOCIATION, "Plan",
                    PlanTerms.monthlyUnlimited(Set.of()), PRICE, null, -1L);

            // Act
            InvalidPlanException ex = assertThrows(InvalidPlanException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("version");
        }
    }

    @Nested
    class Creation {

        @Test
        void createsAMonthlyUnlimitedPlan() {
            // Arrange
            PlanTerms terms = PlanTerms.monthlyUnlimited(Set.of());

            // Act
            Plan plan = PlanFactory.create(ASSOCIATION, "  Unlimited  ", terms, PRICE, null);

            // Assert
            assertThat(plan.id()).isNotNull();
            assertThat(plan.associationId()).isEqualTo(ASSOCIATION);
            assertThat(plan.name()).isEqualTo("Unlimited");
            assertThat(plan.type()).isEqualTo(PlanType.MONTHLY_UNLIMITED);
            assertThat(plan.price()).isEqualTo(PRICE);
            assertThat(plan.validityDays()).isEmpty();
            assertThat(plan.allowedLevels()).isEmpty();
            assertThat(plan.terms()).isEqualTo(terms);
        }

        @Test
        void createsAMonthlyPlanWithAWeeklyAllowance() {
            // Arrange
            PlanTerms terms = PlanTerms.monthlyNPerWeek(2, Set.of(OPEN_PLAY));

            // Act
            Plan plan = PlanFactory.create(ASSOCIATION, "Twice a week", terms, PRICE, null);

            // Assert
            assertThat(plan.type()).isEqualTo(PlanType.MONTHLY_N_PER_WEEK);
            assertThat(plan.terms().sessionsPerWeek()).isEqualTo(2);
            assertThat(plan.terms().credits()).isNull();
            assertThat(plan.allowedLevels()).containsExactly(OPEN_PLAY);
        }

        @Test
        void createsAPackWithCreditsAndValidity() {
            // Arrange
            PlanTerms terms = PlanTerms.pack(10, Set.of());

            // Act
            Plan plan = PlanFactory.create(ASSOCIATION, "10 sessions", terms, PRICE, 90);

            // Assert
            assertThat(plan.type()).isEqualTo(PlanType.PACK);
            assertThat(plan.terms().credits()).isEqualTo(10);
            assertThat(plan.validityDays()).hasValue(90);
        }

        @Test
        void createsASingleSessionWithExactlyOneCredit() {
            // Arrange
            PlanTerms terms = PlanTerms.singleSession(Set.of());

            // Act
            Plan plan = PlanFactory.create(ASSOCIATION, "Drop-in", terms, Money.ofCents(500), 1);

            // Assert
            assertThat(plan.type()).isEqualTo(PlanType.SINGLE_SESSION);
            assertThat(plan.terms().credits()).isEqualTo(1);
            assertThat(plan.validityDays()).hasValue(1);
        }

        @Test
        void copiesTheAllowedLevelsDefensively() {
            // Arrange
            Set<LevelId> mutable = new HashSet<>(Set.of(OPEN_PLAY));
            PlanTerms terms = PlanTerms.pack(5, mutable);

            // Act
            mutable.add(ADVANCED);

            // Assert
            assertThat(terms.allowedLevels()).containsExactly(OPEN_PLAY);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejectsABlankName(String name) {
            // Arrange
            Executable act = () -> PlanFactory.create(ASSOCIATION, name, PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);

            // Act
            InvalidPlanException ex = assertThrows(InvalidPlanException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("name");
        }

        @Test
        void rejectsANullPrice() {
            // Arrange
            Executable act = () -> PlanFactory.create(ASSOCIATION, "Plan", PlanTerms.monthlyUnlimited(Set.of()), null, null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("price");
        }

        @Test
        void rejectsNullTermsAssociationAndId() {
            // Arrange
            PlanTerms terms = PlanTerms.monthlyUnlimited(Set.of());
            Executable noTerms = () -> PlanFactory.create(ASSOCIATION, "Plan", null, PRICE, null);
            Executable noAssociation = () -> PlanFactory.create(null, "Plan", terms, PRICE, null);
            Executable noId = () -> PlanFactory.reconstitute(null, ASSOCIATION, "Plan", terms, PRICE, null, 0L);

            // Act
            NullPointerException termsEx = assertThrows(NullPointerException.class, noTerms);
            NullPointerException associationEx = assertThrows(NullPointerException.class, noAssociation);
            NullPointerException idEx = assertThrows(NullPointerException.class, noId);

            // Assert
            assertThat(termsEx.getMessage()).contains("terms");
            assertThat(associationEx.getMessage()).contains("associationId");
            assertThat(idEx.getMessage()).contains("id");
        }
    }

    @Nested
    class TypeSpecificValidation {

        static Stream<Arguments> invalidTerms() {
            return Stream.of(
                    Arguments.of("unlimited with weekly allowance", PlanType.MONTHLY_UNLIMITED, 2, null),
                    Arguments.of("unlimited with credits", PlanType.MONTHLY_UNLIMITED, null, 10),
                    Arguments.of("weekly without allowance", PlanType.MONTHLY_N_PER_WEEK, null, null),
                    Arguments.of("weekly with zero allowance", PlanType.MONTHLY_N_PER_WEEK, 0, null),
                    Arguments.of("weekly with credits", PlanType.MONTHLY_N_PER_WEEK, 2, 5),
                    Arguments.of("pack without credits", PlanType.PACK, null, null),
                    Arguments.of("pack with zero credits", PlanType.PACK, null, 0),
                    Arguments.of("pack with weekly allowance", PlanType.PACK, 2, 10),
                    Arguments.of("single session without credit", PlanType.SINGLE_SESSION, null, null),
                    Arguments.of("single session with two credits", PlanType.SINGLE_SESSION, null, 2),
                    Arguments.of("single session with weekly allowance", PlanType.SINGLE_SESSION, 1, 1)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidTerms")
        void rejectsTermsThatDoNotMatchTheType(String description, PlanType type, Integer perWeek, Integer credits) {
            // Arrange
            Executable act = () -> new PlanTerms(type, perWeek, credits, Set.of());

            // Act
            InvalidPlanException ex = assertThrows(InvalidPlanException.class, act);

            // Assert
            assertThat(ex.getMessage()).isNotBlank();
        }

        @Test
        void rejectsNullTypeAndNullLevels() {
            // Arrange
            Executable noType = () -> new PlanTerms(null, null, null, Set.of());
            Executable noLevels = () -> PlanTerms.pack(5, null);

            // Act
            NullPointerException type = assertThrows(NullPointerException.class, noType);
            NullPointerException levels = assertThrows(NullPointerException.class, noLevels);

            // Assert
            assertThat(type.getMessage()).contains("type");
            assertThat(levels.getMessage()).contains("allowedLevels");
        }

        static Stream<Arguments> invalidValidity() {
            return Stream.of(
                    Arguments.of("pack without validity", PlanTerms.pack(10, Set.of()), null),
                    Arguments.of("pack with zero validity", PlanTerms.pack(10, Set.of()), 0),
                    Arguments.of("single session without validity", PlanTerms.singleSession(Set.of()), null),
                    Arguments.of("unlimited with validity days", PlanTerms.monthlyUnlimited(Set.of()), 30),
                    Arguments.of("weekly with validity days", PlanTerms.monthlyNPerWeek(2, Set.of()), 30)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidValidity")
        void rejectsValidityThatDoesNotMatchTheType(String description, PlanTerms terms, Integer validityDays) {
            // Arrange
            Executable act = () -> PlanFactory.create(ASSOCIATION, "Plan", terms, PRICE, validityDays);

            // Act
            InvalidPlanException ex = assertThrows(InvalidPlanException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("validityDays");
        }
    }
}
