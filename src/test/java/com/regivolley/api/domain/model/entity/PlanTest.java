package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidPlanException;
import com.regivolley.api.domain.factory.PlanFactory;
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

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanTest {
    private static final AssociationId ASSOCIATION = AssociationId.generate();
    private static final Money PRICE = Money.ofCents(3500);
    private static final LevelId OPEN_PLAY = LevelId.generate();
    private static final LevelId ADVANCED = LevelId.generate();

    @Nested
    class Editing {
        @Test
        void editReplacesNameTermsPriceAndValidityButKeepsIdentityTenantAndVersion() {
            // Arrange
            Plan plan = PlanFactory.reconstitute(PlanId.generate(), ASSOCIATION, "Old", PlanTerms.monthlyUnlimited(Set.of()),
                    PRICE, null, 4L);

            // Act
            Plan edited = plan.edit("  New pack  ", PlanTerms.pack(10, Set.of(OPEN_PLAY)), Money.ofCents(4500), 90);

            // Assert
            assertThat(edited.id()).isEqualTo(plan.id());
            assertThat(edited.associationId()).isEqualTo(ASSOCIATION);
            assertThat(edited.version()).isEqualTo(4L);
            assertThat(edited.name()).isEqualTo("New pack");
            assertThat(edited.type()).isEqualTo(PlanType.PACK);
            assertThat(edited.price()).isEqualTo(Money.ofCents(4500));
            assertThat(edited.validityDays()).hasValue(90);
            assertThat(edited.allowedLevels()).containsExactly(OPEN_PLAY);
        }

        @Test
        void editRevalidatesTheInvariants() {
            // Arrange
            Plan plan = PlanFactory.create(ASSOCIATION, "Plan", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);
            Executable noValidity = () -> plan.edit("Plan", PlanTerms.pack(10, Set.of()), PRICE, null);
            Executable blankName = () -> plan.edit(" ", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);

            // Act
            InvalidPlanException validity = assertThrows(InvalidPlanException.class, noValidity);
            InvalidPlanException name = assertThrows(InvalidPlanException.class, blankName);

            // Assert
            assertThat(validity.getMessage()).contains("validityDays");
            assertThat(name.getMessage()).contains("name");
        }
    }

    @Nested
    class LevelAccess {
        @Test
        void anUnrestrictedPlanAllowsAnyGroup() {
            // Arrange
            PlanTerms terms = PlanTerms.monthlyUnlimited(Set.of());

            // Act
            boolean allowed = terms.allowsAnyOf(Set.of(ADVANCED));

            // Assert
            assertThat(allowed).isTrue();
        }

        @Test
        void aRestrictedPlanAllowsAGroupAcceptingAtLeastOneAllowedLevel() {
            // Arrange
            PlanTerms terms = PlanTerms.pack(10, Set.of(OPEN_PLAY));

            // Act
            boolean allowed = terms.allowsAnyOf(Set.of(OPEN_PLAY, ADVANCED));

            // Assert
            assertThat(allowed).isTrue();
        }

        @Test
        void aRestrictedPlanRejectsAGroupAcceptingNoAllowedLevel() {
            // Arrange
            PlanTerms terms = PlanTerms.pack(10, Set.of(OPEN_PLAY));

            // Act
            boolean allowed = terms.allowsAnyOf(Set.of(ADVANCED));

            // Assert
            assertThat(allowed).isFalse();
        }
    }

    @Nested
    class EndDate {
        static Stream<Arguments> endDates() {
            return Stream.of(
                    Arguments.of("monthly runs to the day before the same date next month",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2026-10-01", "2026-10-31"),
                    Arguments.of("monthly starting mid-month",
                            PlanTerms.monthlyNPerWeek(2, Set.of()), null, "2026-10-15", "2026-11-14"),
                    Arguments.of("monthly starting on the 31st clamps to the short month",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2026-01-31", "2026-02-28"),
                    Arguments.of("monthly starting on the 30th of January ends on the last day of February",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2026-01-30", "2026-02-28"),
                    Arguments.of("monthly starting on the 31st of March ends on 30 April",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2026-03-31", "2026-04-30"),
                    Arguments.of("monthly starting on 29 February of a leap year ends on 28 March",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2028-02-29", "2028-03-28"),
                    Arguments.of("monthly starting on 31 January of a leap year ends on 29 February",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2028-01-31", "2028-02-29"),
                    Arguments.of("monthly starting on the 29th of January in a common year ends on 28 February",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2026-01-29", "2026-02-28"),
                    Arguments.of("monthly starting on the 28th of January ends on 27 February",
                            PlanTerms.monthlyUnlimited(Set.of()), null, "2026-01-28", "2026-02-27"),
                    Arguments.of("pack counts the start day as day one",
                            PlanTerms.pack(10, Set.of()), 90, "2026-10-01", "2026-12-29"),
                    Arguments.of("single session of one day ends the same day",
                            PlanTerms.singleSession(Set.of()), 1, "2026-10-01", "2026-10-01")
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("endDates")
        void computesTheLastInclusiveDay(String description, PlanTerms terms, Integer validityDays, String start,
                                         String expectedEnd) {
            // Arrange
            Plan plan = PlanFactory.create(ASSOCIATION, "Plan", terms, PRICE, validityDays);

            // Act
            LocalDate end = plan.endDateFor(LocalDate.parse(start));

            // Assert
            assertThat(end).isEqualTo(LocalDate.parse(expectedEnd));
        }
    }

    @Test
    void comparesById() {
        // Arrange
        PlanId id = PlanId.generate();
        PlanTerms terms = PlanTerms.monthlyUnlimited(Set.of());
        Plan first = PlanFactory.reconstitute(id, ASSOCIATION, "A", terms, PRICE, null, 0L);
        Plan sameId = PlanFactory.reconstitute(id, ASSOCIATION, "B", terms, PRICE, null, 0L);
        Plan other = PlanFactory.create(ASSOCIATION, "A", terms, PRICE, null);

        // Act
        boolean equalToSameId = first.equals(sameId);
        boolean equalToOther = first.equals(other);

        // Assert
        assertThat(equalToSameId).isTrue();
        assertThat(first).hasSameHashCodeAs(sameId);
        assertThat(equalToOther).isFalse();
        assertThat(first).isNotEqualTo("a plan");
        assertThat(first.toString()).contains(id.toString()).contains("MONTHLY_UNLIMITED");
    }
}
