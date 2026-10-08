package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidSubscriptionException;
import com.regivolley.api.domain.exception.SubscriptionOverlapException;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.CreditUsage;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionFactoryTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();

    private static final MemberId MEMBER = MemberId.generate();

    private static final Money PRICE = Money.ofCents(3500);

    private static final LocalDate TODAY = LocalDate.parse("2026-10-14");

    private static Plan unlimited() {
        return PlanFactory.create(ASSOCIATION, "Unlimited", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);
    }

    private static Plan pack(int credits, int validityDays) {
        return PlanFactory.create(ASSOCIATION, "Pack", PlanTerms.pack(credits, Set.of()), PRICE, validityDays);
    }

    private static Plan single() {
        return PlanFactory.create(ASSOCIATION, "Drop-in", PlanTerms.singleSession(Set.of()), PRICE, 1);
    }

    private static Subscription exhaustedPack() {
        return subscribe(pack(1, 90), "2026-10-01").consume(BookingId.generate(), lisbon("2026-10-05T20:00"));
    }

    private static Subscription subscribe(Plan plan, String start) {
        return SubscriptionFactory.create(plan, MEMBER, LocalDate.parse(start), List.of());
    }

    /** Europe/Lisbon wall-clock time, e.g. {@code "2026-10-14T20:00"}. */
    private static Instant lisbon(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ScheduleZone.LISBON.zoneId()).toInstant();
    }

    @Nested
    class Creation {

        @Test
        void startsPendingWithThePlanSnapshotAndAnEmptyBalanceUsage() {
            // Arrange
            Plan plan = pack(10, 90);

            // Act
            Subscription subscription = SubscriptionFactory.create(plan, MEMBER, LocalDate.parse("2026-10-01"), List.of());

            // Assert
            assertThat(subscription.id()).isNotNull();
            assertThat(subscription.associationId()).isEqualTo(ASSOCIATION);
            assertThat(subscription.memberId()).isEqualTo(MEMBER);
            assertThat(subscription.planId()).isEqualTo(plan.id());
            assertThat(subscription.terms()).isEqualTo(plan.terms());
            assertThat(subscription.type()).isEqualTo(PlanType.PACK);
            assertThat(subscription.startDate()).isEqualTo(LocalDate.parse("2026-10-01"));
            assertThat(subscription.endDate()).isEqualTo(LocalDate.parse("2026-12-29"));
            assertThat(subscription.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(subscription.usages()).isEmpty();
            assertThat(subscription.creditsUsed()).isZero();
            assertThat(subscription.version()).isZero();
        }

        @Test
        void keepsThePriceThePlanHadWhenItWasAssignedEvenIfThePlanChangesLater() {
            // Arrange
            Plan plan = pack(10, 90);
            Subscription subscription = SubscriptionFactory.create(plan, MEMBER, LocalDate.parse("2026-10-01"), List.of());
            Plan repriced = plan.edit(plan.name(), plan.terms(), Money.ofCents(9900), 90);

            // Act
            Subscription renewal = SubscriptionFactory.createRenewal(repriced, subscription, List.of(subscription), LocalDate.parse("2026-12-30"));

            // Assert
            assertThat(subscription.price()).isEqualTo(PRICE);
            assertThat(renewal.price()).isEqualTo(Money.ofCents(9900));
            assertThat(subscription.markPaid().price()).isEqualTo(PRICE);
            assertThat(subscription.consume(BookingId.generate(), lisbon("2026-10-14T20:00")).price()).isEqualTo(PRICE);
        }

        @Test
        void aSubscriptionNeedsAPrice() {
            // Arrange
            Executable act = () -> SubscriptionFactory.reconstitute(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), PlanTerms.monthlyUnlimited(Set.of()), null, LocalDate.parse("2026-10-01"),
                    LocalDate.parse("2026-10-31"), PaymentStatus.PAID, List.of(), 0L);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("price");
        }

        @Test
        void everyChangeKeepsTheVersionTheSubscriptionWasLoadedWith() {
            // Arrange
            Subscription loaded = SubscriptionFactory.reconstitute(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), PlanTerms.pack(5, Set.of()), Money.ofCents(4500),
                    LocalDate.parse("2026-10-01"),
                    LocalDate.parse("2026-12-29"), PaymentStatus.PENDING, List.of(), 4L);
            BookingId booking = BookingId.generate();

            // Act
            Subscription changed = loaded.consume(booking, lisbon("2026-10-12T20:00:00")).markPaid();
            Subscription refunded = changed.refund(booking);

            // Assert
            assertThat(loaded.version()).isEqualTo(4L);
            assertThat(changed.version()).isEqualTo(4L);
            assertThat(refunded.version()).isEqualTo(4L);
        }

        @Test
        void reconstructRejectsANegativeVersion() {
            // Arrange
            Executable act = () -> SubscriptionFactory.reconstitute(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000),
                    LocalDate.parse("2026-10-01"),
                    LocalDate.parse("2026-10-31"), PaymentStatus.PAID, List.of(), -1L);

            // Act
            InvalidSubscriptionException ex = assertThrows(InvalidSubscriptionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("version");
        }

        @Test
        void keepsTheTermsItWasCreatedWithEvenIfThePlanChangesLater() {
            // Arrange
            Plan original = pack(10, 90);
            Subscription subscription = subscribe(original, "2026-10-01");
            Plan edited = PlanFactory.reconstitute(original.id(), ASSOCIATION, "Pack", PlanTerms.pack(20, Set.of()), PRICE, 90, 0L);

            // Act
            int balance = subscription.balanceOn(LocalDate.parse("2026-10-02")).getAsInt();

            // Assert
            assertThat(edited.terms().credits()).isEqualTo(20);
            assertThat(balance).isEqualTo(10);
        }

        @Test
        void rejectsNullArguments() {
            // Arrange
            Plan plan = unlimited();
            LocalDate start = LocalDate.parse("2026-10-01");
            Executable noPlan = () -> SubscriptionFactory.create(null, MEMBER, start, List.of());
            Executable noMember = () -> SubscriptionFactory.create(plan, null, start, List.of());
            Executable noStart = () -> SubscriptionFactory.create(plan, MEMBER, null, List.of());
            Executable noExisting = () -> SubscriptionFactory.create(plan, MEMBER, start, null);

            // Act
            NullPointerException planEx = assertThrows(NullPointerException.class, noPlan);
            NullPointerException memberEx = assertThrows(NullPointerException.class, noMember);
            NullPointerException startEx = assertThrows(NullPointerException.class, noStart);
            NullPointerException existingEx = assertThrows(NullPointerException.class, noExisting);

            // Assert
            assertThat(planEx.getMessage()).contains("plan");
            assertThat(memberEx.getMessage()).contains("memberId");
            assertThat(startEx.getMessage()).contains("startDate");
            assertThat(existingEx.getMessage()).contains("existing");
        }
    }

    @Nested
    class Reconstruction {

        static Stream<String> datesOutsideOctober() {
            return Stream.of("2026-09-30", "2026-11-01");
        }

        private Subscription reconstruct(PlanTerms terms, String start, String end, List<CreditUsage> usages) {
            return SubscriptionFactory.reconstitute(SubscriptionId.generate(), ASSOCIATION, MEMBER, PlanId.generate(), terms, Money.ofCents(3000),
                    LocalDate.parse(start), LocalDate.parse(end), PaymentStatus.PAID, usages, 0L);
        }

        @Test
        void rebuildsAValidSubscription() {
            // Arrange
            CreditUsage usage = new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-05"));

            // Act
            Subscription subscription = reconstruct(PlanTerms.pack(3, Set.of()), "2026-10-01", "2026-10-31", List.of(usage));

            // Assert
            assertThat(subscription.usages()).containsExactly(usage);
            assertThat(subscription.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        void rejectsAnEndBeforeTheStart() {
            // Arrange
            Executable act = () -> reconstruct(PlanTerms.monthlyUnlimited(Set.of()), "2026-10-02", "2026-10-01", List.of());

            // Act
            InvalidSubscriptionException ex = assertThrows(InvalidSubscriptionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("end before");
        }

        @Test
        void acceptsAOneDayPeriod() {
            // Arrange
            // (start equals end)

            // Act
            Subscription subscription = reconstruct(PlanTerms.singleSession(Set.of()), "2026-10-01", "2026-10-01", List.of());

            // Assert
            assertThat(subscription.isValidOn(LocalDate.parse("2026-10-01"))).isTrue();
        }

        @Test
        void rejectsTheSameBookingHoldingTwoPlaces() {
            // Arrange
            BookingId booking = BookingId.generate();
            List<CreditUsage> usages = List.of(
                    new CreditUsage(booking, LocalDate.parse("2026-10-05")),
                    new CreditUsage(booking, LocalDate.parse("2026-10-06")));
            Executable act = () -> reconstruct(PlanTerms.pack(5, Set.of()), "2026-10-01", "2026-10-31", usages);

            // Act
            InvalidSubscriptionException ex = assertThrows(InvalidSubscriptionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("only one place");
        }

        @ParameterizedTest
        @MethodSource("datesOutsideOctober")
        void rejectsASessionOutsideThePeriod(String sessionDate) {
            // Arrange
            List<CreditUsage> usages = List.of(new CreditUsage(BookingId.generate(), LocalDate.parse(sessionDate)));
            Executable act = () -> reconstruct(PlanTerms.pack(5, Set.of()), "2026-10-01", "2026-10-31", usages);

            // Act
            InvalidSubscriptionException ex = assertThrows(InvalidSubscriptionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("outside");
        }

        @Test
        void rejectsMoreCreditsUsedThanThePlanGrants() {
            // Arrange
            List<CreditUsage> usages = List.of(
                    new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-05")),
                    new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-06")));
            Executable act = () -> reconstruct(PlanTerms.pack(1, Set.of()), "2026-10-01", "2026-10-31", usages);

            // Act
            InvalidSubscriptionException ex = assertThrows(InvalidSubscriptionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("credits");
        }

        @Test
        void rejectsMissingRequiredFields() {
            // Arrange
            PlanTerms terms = PlanTerms.monthlyUnlimited(Set.of());
            LocalDate start = LocalDate.parse("2026-10-01");
            Executable noId = () -> SubscriptionFactory.reconstitute(null, ASSOCIATION, MEMBER, PlanId.generate(), terms, Money.ofCents(3000),
                    start, start, PaymentStatus.PAID, List.of(), 0L);
            Executable noStatus = () -> SubscriptionFactory.reconstitute(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), terms, Money.ofCents(3000), start, start, null, List.of(), 0L);

            // Act
            NullPointerException id = assertThrows(NullPointerException.class, noId);
            NullPointerException status = assertThrows(NullPointerException.class, noStatus);

            // Assert
            assertThat(id.getMessage()).contains("id");
            assertThat(status.getMessage()).contains("paymentStatus");
        }
    }

    // ------------------------------------------------------------------ RN-16

    @Nested
    class Overlap {

        private Subscription october() {
            return subscribe(unlimited(), "2026-10-01");
        }

        static Stream<Arguments> overlappingStarts() {
            return Stream.of(
                    Arguments.of("same period", "2026-10-01"),
                    Arguments.of("starts inside", "2026-10-15"),
                    Arguments.of("starts on the last day", "2026-10-31"),
                    Arguments.of("starts before and runs into it", "2026-09-15")
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("overlappingStarts")
        void rejectsAnOverlappingPeriod(String description, String start) {
            // Arrange
            Subscription existing = october();
            Executable act = () -> SubscriptionFactory.create(unlimited(), MEMBER, LocalDate.parse(start), List.of(existing));

            // Act
            SubscriptionOverlapException ex = assertThrows(SubscriptionOverlapException.class, act);

            // Assert
            assertThat(ex.conflictStart()).isEqualTo(LocalDate.parse("2026-10-01"));
            assertThat(ex.conflictEnd()).isEqualTo(LocalDate.parse("2026-10-31"));
            assertThat(ex.getMessage()).contains("01/10/2026").contains("31/10/2026");
        }

        @Test
        void rejectsANewPeriodThatFullyContainsAnExistingOne() {
            // Arrange
            Subscription oneDay = subscribe(single(), "2026-10-14");
            Executable act = () -> SubscriptionFactory.create(unlimited(), MEMBER, LocalDate.parse("2026-10-01"), List.of(oneDay));

            // Act
            SubscriptionOverlapException ex = assertThrows(SubscriptionOverlapException.class, act);

            // Assert
            assertThat(ex.conflictStart()).isEqualTo(LocalDate.parse("2026-10-14"));
        }

        @Test
        void allowsAPeriodStartingTheDayAfterTheExistingOneEnds() {
            // Arrange
            Subscription existing = october();

            // Act
            Subscription next = SubscriptionFactory.create(unlimited(), MEMBER, LocalDate.parse("2026-11-01"), List.of(existing));

            // Assert
            assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void allowsAPeriodEndingTheDayBeforeTheExistingOneStarts() {
            // Arrange
            Subscription existing = october();
            Plan oneDay = single();

            // Act
            Subscription before = SubscriptionFactory.create(oneDay, MEMBER, LocalDate.parse("2026-09-30"), List.of(existing));

            // Assert
            assertThat(before.endDate()).isEqualTo(LocalDate.parse("2026-09-30"));
        }

        @Test
        void ignoresOtherMembersSubscriptions() {
            // Arrange
            Subscription someoneElses = SubscriptionFactory.create(unlimited(), MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());

            // Act
            Subscription mine = SubscriptionFactory.create(unlimited(), MEMBER, LocalDate.parse("2026-10-01"), List.of(someoneElses));

            // Assert
            assertThat(mine.memberId()).isEqualTo(MEMBER);
        }

        @Test
        void overlapsIsInclusiveOnBothEnds() {
            // Arrange
            Subscription existing = october();

            // Act
            boolean touchesStart = existing.overlaps(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-10-01"));
            boolean touchesEnd = existing.overlaps(LocalDate.parse("2026-10-31"), LocalDate.parse("2026-11-20"));
            boolean before = existing.overlaps(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-30"));
            boolean after = existing.overlaps(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-20"));

            // Assert
            assertThat(touchesStart).isTrue();
            assertThat(touchesEnd).isTrue();
            assertThat(before).isFalse();
            assertThat(after).isFalse();
        }
    }

    @Nested
    class Renewal {

        @Test
        void startsTheDayAfterThePreviousEnds() {
            // Arrange
            Subscription previous = subscribe(unlimited(), "2026-10-01");

            // Act
            Subscription renewed = SubscriptionFactory.createRenewal(unlimited(), previous, List.of(previous), TODAY);

            // Assert
            assertThat(previous.renewalStartDate()).isEqualTo(LocalDate.parse("2026-11-01"));
            assertThat(renewed.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
            assertThat(renewed.endDate()).isEqualTo(LocalDate.parse("2026-11-30"));
            assertThat(renewed.memberId()).isEqualTo(MEMBER);
            assertThat(renewed.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void aRenewalIntoADifferentPlanTypeIsAllowed() {
            // Arrange
            Subscription previous = subscribe(unlimited(), "2026-10-01");

            // Act
            Subscription renewed = SubscriptionFactory.createRenewal(pack(10, 90), previous, List.of(previous), TODAY);

            // Assert
            assertThat(renewed.type()).isEqualTo(PlanType.PACK);
            assertThat(renewed.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void isRejectedWhenTheRenewalPeriodAlreadyHasASubscription() {
            // Arrange
            Subscription previous = subscribe(unlimited(), "2026-10-01");
            Subscription alreadyRenewed = SubscriptionFactory.createRenewal(unlimited(), previous, List.of(previous), TODAY);
            Executable act = () -> SubscriptionFactory.createRenewal(unlimited(), previous, List.of(previous, alreadyRenewed), TODAY);

            // Act
            SubscriptionOverlapException ex = assertThrows(SubscriptionOverlapException.class, act);

            // Assert
            assertThat(ex.conflictStart()).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void rejectsANullPrevious() {
            // Arrange
            Executable act = () -> SubscriptionFactory.createRenewal(unlimited(), null, List.of(), TODAY);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("previous");
        }
    }

    @Test
    void aNewPackMayStartInsideTheOldExhaustedOnesPeriod() {
        // Arrange
        Subscription exhausted = exhaustedPack();

        // Act
        Subscription next = SubscriptionFactory.create(pack(10, 90), MEMBER, LocalDate.parse("2026-10-14"), List.of(exhausted));

        // Assert
        assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-10-14"));
    }

    @Test
    void aNewMonthlyPlanMayAlsoStartOverAnExhaustedPack() {
        // Arrange
        Subscription exhausted = exhaustedPack();

        // Act
        Subscription next = SubscriptionFactory.create(unlimited(), MEMBER, LocalDate.parse("2026-10-14"), List.of(exhausted));

        // Assert
        assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-10-14"));
    }

    @Test
    void renewingAnExhaustedPackStartsImmediately() {
        // Arrange
        Subscription exhausted = exhaustedPack();

        // Act
        Subscription renewed = SubscriptionFactory.createRenewal(pack(10, 90), exhausted, List.of(exhausted), TODAY);

        // Assert
        assertThat(exhausted.renewalStartDate(TODAY)).isEqualTo(TODAY);
        assertThat(renewed.startDate()).isEqualTo(TODAY);
        assertThat(renewed.endDate()).isEqualTo(LocalDate.parse("2027-01-11"));
    }
}
