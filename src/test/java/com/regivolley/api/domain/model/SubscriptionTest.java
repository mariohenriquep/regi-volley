package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.InvalidPaymentStatusTransitionException;
import com.regivolley.api.domain.exception.InvalidSubscriptionException;
import com.regivolley.api.domain.exception.SubscriptionOverlapException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();
    private static final MemberId MEMBER = MemberId.generate();
    private static final Money PRICE = Money.ofCents(3500);
    private static final LevelId OPEN_PLAY = LevelId.generate();
    private static final LevelId ADVANCED = LevelId.generate();
    private static final Set<LevelId> ANY_GROUP = Set.of(OPEN_PLAY);
    private static final LocalDate TODAY = LocalDate.parse("2026-10-14");

    private static Plan unlimited() {
        return Plan.create(ASSOCIATION, "Unlimited", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);
    }

    private static Plan perWeek(int sessionsPerWeek) {
        return Plan.create(ASSOCIATION, "N per week", PlanTerms.monthlyNPerWeek(sessionsPerWeek, Set.of()), PRICE, null);
    }

    private static Plan pack(int credits, int validityDays) {
        return Plan.create(ASSOCIATION, "Pack", PlanTerms.pack(credits, Set.of()), PRICE, validityDays);
    }

    private static Plan single() {
        return Plan.create(ASSOCIATION, "Drop-in", PlanTerms.singleSession(Set.of()), PRICE, 1);
    }

    private static Subscription subscribe(Plan plan, String start) {
        return Subscription.create(plan, MEMBER, LocalDate.parse(start), List.of());
    }

    /** Europe/Lisbon wall-clock time, e.g. {@code "2026-10-14T20:00"}. */
    private static Instant lisbon(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BookingPolicy.SCHEDULE_ZONE).toInstant();
    }

    private static Subscription inStatus(Subscription base, PaymentStatus status) {
        return Subscription.reconstruct(base.id(), base.associationId(), base.memberId(), base.planId(),
                base.terms(), base.startDate(), base.endDate(), status, base.usages());
    }

    /** Consumes one place per instant, each for a fresh booking. */
    private static Subscription consumeAll(Subscription subscription, String... sessionStarts) {
        Subscription result = subscription;
        for (String start : sessionStarts) {
            result = result.consume(BookingId.generate(), lisbon(start));
        }
        return result;
    }

    private static Optional<BookingRejectionReason> rejection(Subscription subscription, String sessionStart) {
        return subscription.rejectionFor(lisbon(sessionStart), ANY_GROUP);
    }

    @Nested
    class Creation {

        @Test
        void startsPendingWithThePlanSnapshotAndAnEmptyBalanceUsage() {
            // Arrange
            Plan plan = pack(10, 90);

            // Act
            Subscription subscription = Subscription.create(plan, MEMBER, LocalDate.parse("2026-10-01"), List.of());

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
        }

        @Test
        void keepsTheTermsItWasCreatedWithEvenIfThePlanChangesLater() {
            // Arrange
            Plan original = pack(10, 90);
            Subscription subscription = subscribe(original, "2026-10-01");
            Plan edited = Plan.reconstruct(original.id(), ASSOCIATION, "Pack", PlanTerms.pack(20, Set.of()), PRICE, 90);

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
            Executable noPlan = () -> Subscription.create(null, MEMBER, start, List.of());
            Executable noMember = () -> Subscription.create(plan, null, start, List.of());
            Executable noStart = () -> Subscription.create(plan, MEMBER, null, List.of());
            Executable noExisting = () -> Subscription.create(plan, MEMBER, start, null);

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
            return Subscription.reconstruct(SubscriptionId.generate(), ASSOCIATION, MEMBER, PlanId.generate(), terms,
                    LocalDate.parse(start), LocalDate.parse(end), PaymentStatus.PAID, usages);
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
            Executable noId = () -> Subscription.reconstruct(null, ASSOCIATION, MEMBER, PlanId.generate(), terms,
                    start, start, PaymentStatus.PAID, List.of());
            Executable noStatus = () -> Subscription.reconstruct(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), terms, start, start, null, List.of());

            // Act
            NullPointerException id = assertThrows(NullPointerException.class, noId);
            NullPointerException status = assertThrows(NullPointerException.class, noStatus);

            // Assert
            assertThat(id.getMessage()).contains("id");
            assertThat(status.getMessage()).contains("paymentStatus");
        }
    }

    // ---------------------------------------------------------------- RN-13 balance rules

    @Nested
    class MonthlyUnlimited {

        @Test
        void hasNoLimitOnTheNumberOfSessions() {
            // Arrange
            Subscription subscription = subscribe(unlimited(), "2026-10-01");

            // Act
            Subscription used = consumeAll(subscription, "2026-10-05T20:00", "2026-10-06T20:00", "2026-10-07T20:00",
                    "2026-10-08T20:00", "2026-10-09T20:00", "2026-10-10T20:00", "2026-10-12T20:00");

            // Assert
            assertThat(used.creditsUsed()).isEqualTo(7);
            assertThat(used.balanceOn(LocalDate.parse("2026-10-13"))).isEmpty();
            assertThat(rejection(used, "2026-10-13T20:00")).isEmpty();
        }

        @Test
        void needsOnlyValidity() {
            // Arrange
            Subscription subscription = subscribe(unlimited(), "2026-10-01");

            // Act
            Optional<BookingRejectionReason> afterEnd = rejection(subscription, "2026-11-01T20:00");

            // Assert
            assertThat(afterEnd).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }
    }

    @Nested
    class MonthlyNPerWeek {

        @Test
        void allowsNSessionsInAWeekAndRejectsTheNextOne() {
            // Arrange
            Subscription subscription = subscribe(perWeek(2), "2026-10-01");
            Subscription used = consumeAll(subscription, "2026-10-12T20:00", "2026-10-14T20:00");

            // Act
            Optional<BookingRejectionReason> third = rejection(used, "2026-10-16T20:00");

            // Assert
            assertThat(used.balanceOn(LocalDate.parse("2026-10-16")).getAsInt()).isZero();
            assertThat(third).contains(BookingRejectionReason.NO_BALANCE);
        }

        @Test
        void theWeekRunsMondayToSundayAndTheNextMondayStartsAFreshAllowance() {
            // Arrange
            Subscription subscription = subscribe(perWeek(2), "2026-10-01");
            Subscription used = consumeAll(subscription, "2026-10-17T20:00", "2026-10-18T22:00");

            // Act
            Optional<BookingRejectionReason> sundayAgain = rejection(used, "2026-10-18T23:30");
            Optional<BookingRejectionReason> monday = rejection(used, "2026-10-19T00:00");

            // Assert
            assertThat(DayOfWeek.from(LocalDate.parse("2026-10-18"))).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(sundayAgain).contains(BookingRejectionReason.NO_BALANCE);
            assertThat(monday).isEmpty();
            assertThat(used.balanceOn(LocalDate.parse("2026-10-19")).getAsInt()).isEqualTo(2);
        }

        @Test
        void sessionsOnBothSidesOfAWeekBoundaryDoNotCountTogether() {
            // Arrange
            Subscription subscription = subscribe(perWeek(1), "2026-10-01");
            Subscription used = consumeAll(subscription, "2026-10-18T20:00");

            // Act
            Subscription nextWeek = used.consume(BookingId.generate(), lisbon("2026-10-19T20:00"));

            // Assert
            assertThat(nextWeek.creditsUsed()).isEqualTo(2);
        }

        @Test
        void allowsSessionsInOtherWeeksThatAlreadyUsedTheirAllowance() {
            // Arrange
            Subscription subscription = subscribe(perWeek(1), "2026-10-01");
            Subscription used = consumeAll(subscription, "2026-10-14T20:00");

            // Act
            Optional<BookingRejectionReason> sameWeek = rejection(used, "2026-10-15T20:00");
            Optional<BookingRejectionReason> previousWeek = rejection(used, "2026-10-08T20:00");

            // Assert
            assertThat(sameWeek).contains(BookingRejectionReason.NO_BALANCE);
            assertThat(previousWeek).isEmpty();
        }

        static Stream<Arguments> dstWeeks() {
            return Stream.of(
                    // Spring forward: Sunday 29 Mar 2026 (01:00 UTC). 2026-03-29T23:30Z is Monday 00:30 in Lisbon.
                    Arguments.of("2026-03-15", "2026-03-25T20:00", "2026-03-29T22:30:00Z", "2026-03-29T23:30:00Z"),
                    // Fall back: Sunday 25 Oct 2026. 2026-10-24T23:30Z is still Sunday 00:30 WEST; 2026-10-26T00:30Z is Monday.
                    Arguments.of("2026-10-01", "2026-10-21T20:00", "2026-10-24T23:30:00Z", "2026-10-26T00:30:00Z")
            );
        }

        @ParameterizedTest(name = "week with a DST change, subscription from {0}")
        @MethodSource("dstWeeks")
        void countsTheWeekInLisbonTimeAcrossADstChange(String start, String firstSession, String lastInstantOfTheWeek,
                                                       String firstInstantOfTheNextWeek) {
            // Arrange
            Subscription subscription = subscribe(perWeek(1), start);
            Subscription used = consumeAll(subscription, firstSession);

            // Act
            Optional<BookingRejectionReason> endOfWeek = used.rejectionFor(Instant.parse(lastInstantOfTheWeek), ANY_GROUP);
            Optional<BookingRejectionReason> nextWeek = used.rejectionFor(Instant.parse(firstInstantOfTheNextWeek), ANY_GROUP);

            // Assert
            assertThat(endOfWeek).contains(BookingRejectionReason.NO_BALANCE);
            assertThat(nextWeek).isEmpty();
        }

        @Test
        void theLisbonWeekStartsBeforeTheUtcOneInSummer() {
            // Arrange
            Instant mondayMidnightLisbon = Instant.parse("2026-03-29T23:30:00Z");

            // Act
            DayOfWeek lisbonDay = mondayMidnightLisbon.atZone(BookingPolicy.SCHEDULE_ZONE).getDayOfWeek();
            DayOfWeek utcDay = mondayMidnightLisbon.atZone(ZoneOffset.UTC).getDayOfWeek();

            // Assert
            assertThat(lisbonDay).isEqualTo(DayOfWeek.MONDAY);
            assertThat(utcDay).isEqualTo(DayOfWeek.SUNDAY);
        }

        @Test
        void aRefundGivesTheWeeklyPlaceBack() {
            // Arrange
            Subscription subscription = subscribe(perWeek(1), "2026-10-01");
            BookingId booking = BookingId.generate();
            Subscription used = subscription.consume(booking, lisbon("2026-10-14T20:00"));

            // Act
            Subscription refunded = used.refund(booking);

            // Assert
            assertThat(rejection(used, "2026-10-15T20:00")).contains(BookingRejectionReason.NO_BALANCE);
            assertThat(rejection(refunded, "2026-10-15T20:00")).isEmpty();
        }
    }

    @Nested
    class Pack {

        @Test
        void spendsOneCreditPerBookingUntilItRunsOut() {
            // Arrange
            Subscription subscription = subscribe(pack(2, 90), "2026-10-01");
            Subscription used = consumeAll(subscription, "2026-10-05T20:00", "2026-10-12T20:00");

            // Act
            Optional<BookingRejectionReason> third = rejection(used, "2026-10-19T20:00");

            // Assert
            assertThat(used.balanceOn(LocalDate.parse("2026-10-19")).getAsInt()).isZero();
            assertThat(third).contains(BookingRejectionReason.NO_BALANCE);
        }

        @Test
        void creditsAreNotLimitedPerWeek() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");

            // Act
            Subscription used = consumeAll(subscription, "2026-10-12T20:00", "2026-10-13T20:00", "2026-10-14T20:00");

            // Assert
            assertThat(used.balanceOn(LocalDate.parse("2026-10-15")).getAsInt()).isEqualTo(2);
        }

        @Test
        void isValidOnTheLastDayAndExpiredTheDayAfter() {
            // Arrange
            Subscription subscription = subscribe(pack(10, 90), "2026-10-01");

            // Act
            Optional<BookingRejectionReason> lastDay = rejection(subscription, "2026-12-29T23:30");
            Optional<BookingRejectionReason> dayAfter = rejection(subscription, "2026-12-30T00:00");

            // Assert
            assertThat(lastDay).isEmpty();
            assertThat(dayAfter).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }

        @Test
        void isValidOnTheFirstDayButNotTheDayBefore() {
            // Arrange
            Subscription subscription = subscribe(pack(10, 90), "2026-10-01");

            // Act
            Optional<BookingRejectionReason> firstDay = rejection(subscription, "2026-10-01T00:00");
            Optional<BookingRejectionReason> dayBefore = rejection(subscription, "2026-09-30T23:59");

            // Assert
            assertThat(firstDay).isEmpty();
            assertThat(dayBefore).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }

        @Test
        void expiryIsJudgedOnTheLisbonDateNotTheUtcDate() {
            // Arrange
            Subscription subscription = subscribe(pack(10, 30), "2026-06-01");
            Instant justAfterMidnightInLisbon = Instant.parse("2026-06-30T23:30:00Z");

            // Act
            Optional<BookingRejectionReason> result = subscription.rejectionFor(justAfterMidnightInLisbon, ANY_GROUP);

            // Assert
            assertThat(subscription.endDate()).isEqualTo(LocalDate.parse("2026-06-30"));
            assertThat(result).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }

        @Test
        void unusedCreditsAreWorthNothingAfterExpiry() {
            // Arrange
            Subscription subscription = subscribe(pack(10, 90), "2026-10-01");

            // Act
            Executable act = () -> subscription.consume(BookingId.generate(), lisbon("2026-12-30T20:00"));

            // Assert
            BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);
            assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }
    }

    @Nested
    class SingleSession {

        @Test
        void allowsExactlyOneSessionOnItsDay() {
            // Arrange
            Subscription subscription = subscribe(single(), "2026-10-14");
            Subscription used = consumeAll(subscription, "2026-10-14T20:00");

            // Act
            Optional<BookingRejectionReason> second = rejection(used, "2026-10-14T21:00");
            Optional<BookingRejectionReason> nextDay = rejection(subscription, "2026-10-15T20:00");

            // Assert
            assertThat(second).contains(BookingRejectionReason.NO_BALANCE);
            assertThat(nextDay).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }
    }

    // ------------------------------------------------------- RN-15 consume / refund

    @Nested
    class ConsumeAndRefund {

        @Test
        void consumingRecordsTheBookingAndTheLisbonSessionDate() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");
            BookingId booking = BookingId.generate();

            // Act
            Subscription used = subscription.consume(booking, Instant.parse("2026-10-14T23:30:00Z"));

            // Assert
            assertThat(used.holdsPlaceFor(booking)).isTrue();
            assertThat(used.usages()).containsExactly(new CreditUsage(booking, LocalDate.parse("2026-10-15")));
            assertThat(subscription.creditsUsed()).isZero();
        }

        @Test
        void consumingTwiceForTheSameBookingIsANoOp() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");
            BookingId booking = BookingId.generate();
            Subscription once = subscription.consume(booking, lisbon("2026-10-14T20:00"));

            // Act
            Subscription twice = once.consume(booking, lisbon("2026-10-14T20:00"));

            // Assert
            assertThat(twice).isSameAs(once);
            assertThat(twice.creditsUsed()).isEqualTo(1);
        }

        @Test
        void aRetriedConsumeStillSucceedsWhenTheLastCreditWasTheBookings() {
            // Arrange
            Subscription subscription = subscribe(single(), "2026-10-14");
            BookingId booking = BookingId.generate();
            Subscription once = subscription.consume(booking, lisbon("2026-10-14T20:00"));

            // Act
            Subscription retried = once.consume(booking, lisbon("2026-10-14T20:00"));

            // Assert
            assertThat(retried).isSameAs(once);
        }

        @Test
        void refundingGivesTheCreditBack() {
            // Arrange
            Subscription subscription = subscribe(pack(1, 90), "2026-10-01");
            BookingId booking = BookingId.generate();
            Subscription used = subscription.consume(booking, lisbon("2026-10-14T20:00"));

            // Act
            Subscription refunded = used.refund(booking);

            // Assert
            assertThat(refunded.holdsPlaceFor(booking)).isFalse();
            assertThat(refunded.balanceOn(LocalDate.parse("2026-10-14")).getAsInt()).isEqualTo(1);
            assertThat(used.balanceOn(LocalDate.parse("2026-10-14")).getAsInt()).isZero();
        }

        @Test
        void refundingTwiceNeverGivesMoreThanWasTaken() {
            // Arrange
            Subscription subscription = subscribe(pack(2, 90), "2026-10-01");
            BookingId first = BookingId.generate();
            BookingId second = BookingId.generate();
            Subscription used = subscription.consume(first, lisbon("2026-10-14T20:00"))
                    .consume(second, lisbon("2026-10-15T20:00"));
            Subscription refundedOnce = used.refund(first);

            // Act
            Subscription refundedTwice = refundedOnce.refund(first);

            // Assert
            assertThat(refundedTwice).isSameAs(refundedOnce);
            assertThat(refundedTwice.creditsUsed()).isEqualTo(1);
            assertThat(refundedTwice.holdsPlaceFor(second)).isTrue();
        }

        @Test
        void refundingABookingThatNeverConsumedIsANoOp() {
            // Arrange
            Subscription subscription = subscribe(pack(2, 90), "2026-10-01");

            // Act
            Subscription refunded = subscription.refund(BookingId.generate());

            // Assert
            assertThat(refunded).isSameAs(subscription);
        }

        @Test
        void refundsStillWorkAfterTheSubscriptionExpiredOrWentOverdue() {
            // Arrange
            Subscription subscription = subscribe(pack(2, 10), "2026-10-01");
            BookingId booking = BookingId.generate();
            Subscription overdue = subscription.consume(booking, lisbon("2026-10-05T20:00")).markOverdue();

            // Act
            Subscription refunded = overdue.refund(booking);

            // Assert
            assertThat(refunded.creditsUsed()).isZero();
            assertThat(refunded.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
        }

        @Test
        void consumingWithoutBalanceIsRejected() {
            // Arrange
            Subscription used = consumeAll(subscribe(single(), "2026-10-14"), "2026-10-14T20:00");
            Executable act = () -> used.consume(BookingId.generate(), lisbon("2026-10-14T21:00"));

            // Act
            BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

            // Assert
            assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_BALANCE);
            assertThat(ex.getMessage()).contains("no balance");
        }

        @Test
        void consumingOnAnOverdueSubscriptionIsRejected() {
            // Arrange
            Subscription overdue = subscribe(unlimited(), "2026-10-01").markOverdue();
            Executable act = () -> overdue.consume(BookingId.generate(), lisbon("2026-10-14T20:00"));

            // Act
            BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

            // Assert
            assertThat(ex.reason()).isEqualTo(BookingRejectionReason.PAYMENT_OVERDUE);
        }

        @Test
        void consumingOnAPendingSubscriptionIsAllowed() {
            // Arrange
            Subscription pending = subscribe(unlimited(), "2026-10-01");

            // Act
            Subscription used = pending.consume(BookingId.generate(), lisbon("2026-10-14T20:00"));

            // Assert
            assertThat(pending.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(used.creditsUsed()).isEqualTo(1);
        }

        @Test
        void rejectsNullBookingIds() {
            // Arrange
            Subscription subscription = subscribe(unlimited(), "2026-10-01");
            Executable consume = () -> subscription.consume(null, lisbon("2026-10-14T20:00"));
            Executable refund = () -> subscription.refund(null);

            // Act
            NullPointerException onConsume = assertThrows(NullPointerException.class, consume);
            NullPointerException onRefund = assertThrows(NullPointerException.class, refund);

            // Assert
            assertThat(onConsume.getMessage()).contains("bookingId");
            assertThat(onRefund.getMessage()).contains("bookingId");
        }
    }

    // ------------------------------------------------------------ rejection order

    @Nested
    class RejectionOrder {

        @Test
        void reportsAnOverdueSubscriptionWithBalance() {
            // Arrange
            Subscription overdue = subscribe(unlimited(), "2026-10-01").markOverdue();

            // Act
            Optional<BookingRejectionReason> reason = rejection(overdue, "2026-10-14T20:00");

            // Assert
            assertThat(reason).contains(BookingRejectionReason.PAYMENT_OVERDUE);
        }

        @Test
        void reportsNoBalanceBeforeOverdueBecausePayingDoesNotFixIt() {
            // Arrange
            Subscription exhausted = consumeAll(subscribe(single(), "2026-10-14"), "2026-10-14T20:00").markOverdue();

            // Act
            Optional<BookingRejectionReason> reason = rejection(exhausted, "2026-10-14T21:00");

            // Assert
            assertThat(reason).contains(BookingRejectionReason.NO_BALANCE);
        }

        @Test
        void reportsThePlanLevelBeforeBalanceAndOverdue() {
            // Arrange
            Plan restricted = Plan.create(ASSOCIATION, "Open play only", PlanTerms.pack(5, Set.of(OPEN_PLAY)), PRICE, 90);
            Subscription overdue = subscribe(restricted, "2026-10-01").markOverdue();

            // Act
            Optional<BookingRejectionReason> reason = overdue.rejectionFor(lisbon("2026-10-14T20:00"), Set.of(ADVANCED));

            // Assert
            assertThat(reason).contains(BookingRejectionReason.PLAN_LEVEL_NOT_ALLOWED);
        }

        @Test
        void reportsOutsideThePeriodBeforeEverythingElse() {
            // Arrange
            Plan restricted = Plan.create(ASSOCIATION, "Open play only", PlanTerms.pack(5, Set.of(OPEN_PLAY)), PRICE, 10);
            Subscription overdue = subscribe(restricted, "2026-10-01").markOverdue();

            // Act
            Optional<BookingRejectionReason> reason = overdue.rejectionFor(lisbon("2026-11-14T20:00"), Set.of(ADVANCED));

            // Assert
            assertThat(reason).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }

        @Test
        void rejectsNullGroupLevels() {
            // Arrange
            Subscription subscription = subscribe(unlimited(), "2026-10-01");
            Executable act = () -> subscription.rejectionFor(lisbon("2026-10-14T20:00"), null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("groupLevels");
        }
    }

    // ---------------------------------------------------------------- RN-18 payment

    @Nested
    class PaymentTransitions {

        @Test
        void pendingBecomesPaid() {
            // Arrange
            Subscription pending = subscribe(unlimited(), "2026-10-01");

            // Act
            Subscription paid = pending.markPaid();

            // Assert
            assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(pending.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void pendingBecomesOverdue() {
            // Arrange
            Subscription pending = subscribe(unlimited(), "2026-10-01");

            // Act
            Subscription overdue = pending.markOverdue();

            // Assert
            assertThat(overdue.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
        }

        @Test
        void overdueBecomesPaid() {
            // Arrange
            Subscription overdue = inStatus(subscribe(unlimited(), "2026-10-01"), PaymentStatus.OVERDUE);

            // Act
            Subscription paid = overdue.markPaid();

            // Assert
            assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        void aTransitionKeepsTheUsagesAndIdentity() {
            // Arrange
            Subscription used = consumeAll(subscribe(pack(3, 90), "2026-10-01"), "2026-10-14T20:00");

            // Act
            Subscription paid = used.markPaid();

            // Assert
            assertThat(paid.usages()).isEqualTo(used.usages());
            assertThat(paid.id()).isEqualTo(used.id());
            assertThat(paid.endDate()).isEqualTo(used.endDate());
        }

        static Stream<Arguments> illegalTransitions() {
            return Stream.of(
                    Arguments.of(PaymentStatus.PAID, PaymentStatus.PAID),
                    Arguments.of(PaymentStatus.PAID, PaymentStatus.OVERDUE),
                    Arguments.of(PaymentStatus.OVERDUE, PaymentStatus.OVERDUE)
            );
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("illegalTransitions")
        void rejectsATransitionTheMapDoesNotAllow(PaymentStatus from, PaymentStatus to) {
            // Arrange
            Subscription subscription = inStatus(subscribe(unlimited(), "2026-10-01"), from);
            Executable act = () -> {
                if (to == PaymentStatus.PAID) {
                    subscription.markPaid();
                } else {
                    subscription.markOverdue();
                }
            };

            // Act
            InvalidPaymentStatusTransitionException ex = assertThrows(InvalidPaymentStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(from);
            assertThat(ex.to()).isEqualTo(to);
        }

        @ParameterizedTest
        @EnumSource(PaymentStatus.class)
        void theMessageNamesBothStatusesInPlainEnglish(PaymentStatus status) {
            // Arrange
            InvalidPaymentStatusTransitionException ex = new InvalidPaymentStatusTransitionException(status, status);

            // Act
            String message = ex.getMessage();

            // Assert
            assertThat(message).startsWith("Cannot move the subscription payment from ")
                    .contains(status.name().toLowerCase());
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
            Executable act = () -> Subscription.create(unlimited(), MEMBER, LocalDate.parse(start), List.of(existing));

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
            Executable act = () -> Subscription.create(unlimited(), MEMBER, LocalDate.parse("2026-10-01"), List.of(oneDay));

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
            Subscription next = Subscription.create(unlimited(), MEMBER, LocalDate.parse("2026-11-01"), List.of(existing));

            // Assert
            assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void allowsAPeriodEndingTheDayBeforeTheExistingOneStarts() {
            // Arrange
            Subscription existing = october();
            Plan oneDay = single();

            // Act
            Subscription before = Subscription.create(oneDay, MEMBER, LocalDate.parse("2026-09-30"), List.of(existing));

            // Assert
            assertThat(before.endDate()).isEqualTo(LocalDate.parse("2026-09-30"));
        }

        @Test
        void ignoresOtherMembersSubscriptions() {
            // Arrange
            Subscription someoneElses = Subscription.create(unlimited(), MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());

            // Act
            Subscription mine = Subscription.create(unlimited(), MEMBER, LocalDate.parse("2026-10-01"), List.of(someoneElses));

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
            Subscription renewed = Subscription.renew(unlimited(), previous, List.of(previous), TODAY);

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
            Subscription renewed = Subscription.renew(pack(10, 90), previous, List.of(previous), TODAY);

            // Assert
            assertThat(renewed.type()).isEqualTo(PlanType.PACK);
            assertThat(renewed.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void isRejectedWhenTheRenewalPeriodAlreadyHasASubscription() {
            // Arrange
            Subscription previous = subscribe(unlimited(), "2026-10-01");
            Subscription alreadyRenewed = Subscription.renew(unlimited(), previous, List.of(previous), TODAY);
            Executable act = () -> Subscription.renew(unlimited(), previous, List.of(previous, alreadyRenewed), TODAY);

            // Act
            SubscriptionOverlapException ex = assertThrows(SubscriptionOverlapException.class, act);

            // Assert
            assertThat(ex.conflictStart()).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void rejectsANullPrevious() {
            // Arrange
            Executable act = () -> Subscription.renew(unlimited(), null, List.of(), TODAY);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("previous");
        }
    }

    // ------------------------------------------------- RN-16 decisions (exhausted packs, weekly)

    @Nested
    class ExhaustedCreditPlans {

        private Subscription exhaustedPack() {
            return subscribe(pack(1, 90), "2026-10-01").consume(BookingId.generate(), lisbon("2026-10-05T20:00"));
        }

        @Test
        void aPackWithoutCreditsLeftIsExhaustedAndOneWithCreditsIsNot() {
            // Arrange
            Subscription fresh = subscribe(pack(2, 90), "2026-10-01");

            // Act
            Subscription oneLeft = fresh.consume(BookingId.generate(), lisbon("2026-10-05T20:00"));
            Subscription none = oneLeft.consume(BookingId.generate(), lisbon("2026-10-06T20:00"));

            // Assert
            assertThat(fresh.isExhausted()).isFalse();
            assertThat(oneLeft.isExhausted()).isFalse();
            assertThat(none.isExhausted()).isTrue();
        }

        @Test
        void monthlyPlansAreNeverExhausted() {
            // Arrange
            Subscription used = consumeAll(subscribe(perWeek(1), "2026-10-01"), "2026-10-14T20:00");

            // Act
            boolean exhausted = used.isExhausted();

            // Assert
            assertThat(exhausted).isFalse();
        }

        @Test
        void aNewPackMayStartInsideTheOldExhaustedOnesPeriod() {
            // Arrange
            Subscription exhausted = exhaustedPack();

            // Act
            Subscription next = Subscription.create(pack(10, 90), MEMBER, LocalDate.parse("2026-10-14"), List.of(exhausted));

            // Assert
            assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-10-14"));
        }

        @Test
        void aNewMonthlyPlanMayAlsoStartOverAnExhaustedPack() {
            // Arrange
            Subscription exhausted = exhaustedPack();

            // Act
            Subscription next = Subscription.create(unlimited(), MEMBER, LocalDate.parse("2026-10-14"), List.of(exhausted));

            // Assert
            assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-10-14"));
        }

        @Test
        void anExhaustedSingleSessionDoesNotBlockANewPackEither() {
            // Arrange
            Subscription exhausted = consumeAll(subscribe(single(), "2026-10-14"), "2026-10-14T20:00");

            // Act
            Subscription next = Subscription.create(pack(10, 90), MEMBER, LocalDate.parse("2026-10-14"), List.of(exhausted));

            // Assert
            assertThat(exhausted.isExhausted()).isTrue();
            assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-10-14"));
        }

        @Test
        void aPackWithCreditsLeftStillBlocksAnOverlappingSubscription() {
            // Arrange
            Subscription active = subscribe(pack(2, 90), "2026-10-01");
            Executable act = () -> Subscription.create(pack(10, 90), MEMBER, LocalDate.parse("2026-10-14"), List.of(active));

            // Act
            SubscriptionOverlapException ex = assertThrows(SubscriptionOverlapException.class, act);

            // Assert
            assertThat(ex.conflictStart()).isEqualTo(LocalDate.parse("2026-10-01"));
        }

        @Test
        void aMonthlySubscriptionBlocksANewPackEvenWhenItsWeeklyAllowanceIsUsedUp() {
            // Arrange
            Subscription monthly = consumeAll(subscribe(perWeek(1), "2026-10-01"), "2026-10-14T20:00");
            Executable act = () -> Subscription.create(pack(10, 90), MEMBER, LocalDate.parse("2026-10-15"), List.of(monthly));

            // Act
            SubscriptionOverlapException ex = assertThrows(SubscriptionOverlapException.class, act);

            // Assert
            assertThat(ex.conflictEnd()).isEqualTo(LocalDate.parse("2026-10-31"));
        }

        @Test
        void aRefundMakesAnExhaustedPackActiveAgain() {
            // Arrange
            BookingId booking = BookingId.generate();
            Subscription exhausted = subscribe(pack(1, 90), "2026-10-01").consume(booking, lisbon("2026-10-05T20:00"));

            // Act
            Subscription refunded = exhausted.refund(booking);

            // Assert
            assertThat(exhausted.isExhausted()).isTrue();
            assertThat(refunded.isExhausted()).isFalse();
        }

        @Test
        void renewingAnExhaustedPackStartsImmediately() {
            // Arrange
            Subscription exhausted = exhaustedPack();

            // Act
            Subscription renewed = Subscription.renew(pack(10, 90), exhausted, List.of(exhausted), TODAY);

            // Assert
            assertThat(exhausted.renewalStartDate(TODAY)).isEqualTo(TODAY);
            assertThat(renewed.startDate()).isEqualTo(TODAY);
            assertThat(renewed.endDate()).isEqualTo(LocalDate.parse("2027-01-11"));
        }

        @Test
        void renewingAnExhaustedPackAfterItsPeriodStartsTheDayAfterItEnded() {
            // Arrange
            Subscription exhausted = subscribe(pack(1, 10), "2026-10-01")
                    .consume(BookingId.generate(), lisbon("2026-10-05T20:00"));

            // Act
            LocalDate start = exhausted.renewalStartDate(LocalDate.parse("2026-10-20"));

            // Assert
            assertThat(start).isEqualTo(LocalDate.parse("2026-10-11"));
        }

        @Test
        void renewingAnExhaustedPackBeforeItStartsKeepsTheNormalRenewalDate() {
            // Arrange
            Subscription exhausted = subscribe(pack(1, 10), "2026-10-10")
                    .consume(BookingId.generate(), lisbon("2026-10-12T20:00"));

            // Act
            LocalDate start = exhausted.renewalStartDate(LocalDate.parse("2026-10-05"));

            // Assert
            assertThat(start).isEqualTo(LocalDate.parse("2026-10-20"));
        }

        @Test
        void renewingAPackWithCreditsLeftWaitsForItToEnd() {
            // Arrange
            Subscription active = subscribe(pack(5, 90), "2026-10-01");

            // Act
            Subscription renewed = Subscription.renew(pack(10, 90), active, List.of(active), TODAY);

            // Assert
            assertThat(renewed.startDate()).isEqualTo(LocalDate.parse("2026-12-30"));
        }

        @Test
        void renewingAMonthlyPlanAlwaysStartsTheDayAfterItEnds() {
            // Arrange
            Subscription monthly = subscribe(unlimited(), "2026-10-01");

            // Act
            LocalDate start = monthly.renewalStartDate(LocalDate.parse("2026-10-14"));

            // Assert
            assertThat(start).isEqualTo(LocalDate.parse("2026-11-01"));
        }

        @Test
        void rejectsANullToday() {
            // Arrange
            Subscription subscription = subscribe(unlimited(), "2026-10-01");
            Executable act = () -> subscription.renewalStartDate(null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("today");
        }
    }

    @Nested
    class WeeklyAllowanceAcrossSubscriptions {

        // Previous monthly plan 16/09 - 15/10; its renewal starts on Friday 16/10, mid-week
        // (Monday 12/10 - Sunday 18/10).
        private Subscription previousUsedTwice() {
            return consumeAll(subscribe(perWeek(2), "2026-09-16"), "2026-10-12T20:00", "2026-10-13T20:00");
        }

        private Subscription renewal(Subscription previous) {
            return Subscription.renew(perWeek(2), previous, List.of(previous), TODAY);
        }

        @Test
        void aMidWeekRenewalDoesNotDoubleTheLimit() {
            // Arrange
            Subscription previous = previousUsedTwice();
            Subscription renewal = renewal(previous);

            // Act
            Optional<BookingRejectionReason> alone = renewal.rejectionFor(lisbon("2026-10-17T20:00"), ANY_GROUP);
            Optional<BookingRejectionReason> memberWide = renewal.rejectionFor(lisbon("2026-10-17T20:00"), ANY_GROUP,
                    List.of(previous, renewal));

            // Assert
            assertThat(renewal.startDate()).isEqualTo(LocalDate.parse("2026-10-16"));
            assertThat(alone).isEmpty();
            assertThat(memberWide).contains(BookingRejectionReason.NO_BALANCE);
        }

        @Test
        void theNextWeekStartsFreshAcrossSubscriptions() {
            // Arrange
            Subscription previous = previousUsedTwice();
            Subscription renewal = renewal(previous);

            // Act
            Optional<BookingRejectionReason> monday = renewal.rejectionFor(lisbon("2026-10-19T20:00"), ANY_GROUP,
                    List.of(previous, renewal));

            // Assert
            assertThat(monday).isEmpty();
        }

        @Test
        void usagesInTheRenewalAlsoCountForTheWeekBalance() {
            // Arrange
            Subscription previous = consumeAll(subscribe(perWeek(2), "2026-09-16"), "2026-10-13T20:00");
            Subscription renewal = consumeAll(renewal(previous), "2026-10-16T20:00");

            // Act
            int balance = renewal.balanceOn(LocalDate.parse("2026-10-17"), List.of(previous, renewal)).getAsInt();

            // Assert
            assertThat(balance).isZero();
            assertThat(renewal.balanceOn(LocalDate.parse("2026-10-17")).getAsInt()).isEqualTo(1);
        }

        @Test
        void consumeWithTheMembersSubscriptionsRefusesWhereEligibilityRefuses() {
            // Arrange
            Subscription previous = previousUsedTwice();
            Subscription renewal = renewal(previous);
            Executable act = () -> renewal.consume(BookingId.generate(), lisbon("2026-10-17T20:00"),
                    List.of(previous, renewal));

            // Act
            BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

            // Assert
            assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_BALANCE);
        }

        @Test
        void consumeWithTheMembersSubscriptionsSucceedsWhenThereIsRoom() {
            // Arrange
            Subscription previous = consumeAll(subscribe(perWeek(2), "2026-09-16"), "2026-10-13T20:00");
            Subscription renewal = renewal(previous);

            // Act
            Subscription used = renewal.consume(BookingId.generate(), lisbon("2026-10-17T20:00"), List.of(previous, renewal));

            // Assert
            assertThat(used.creditsUsed()).isEqualTo(1);
        }

        @Test
        void consumeWithoutTheMembersSubscriptionsJudgesOnlyThisOne() {
            // Arrange
            Subscription previous = previousUsedTwice();
            Subscription renewal = renewal(previous);

            // Act
            Subscription used = renewal.consume(BookingId.generate(), lisbon("2026-10-17T20:00"));

            // Assert
            assertThat(used.creditsUsed()).isEqualTo(1);
        }

        @Test
        void packUsagesAndOtherMembersUsagesDoNotCountAgainstTheWeeklyAllowance() {
            // Arrange
            Subscription weekly = subscribe(perWeek(1), "2026-10-01");
            Subscription myPack = consumeAll(subscribe(pack(5, 90), "2026-10-01"), "2026-10-14T20:00");
            Subscription someoneElses = consumeAll(Subscription.create(perWeek(1), MemberId.generate(),
                    LocalDate.parse("2026-10-01"), List.of()), "2026-10-14T20:00");

            // Act
            Optional<BookingRejectionReason> reason = weekly.rejectionFor(lisbon("2026-10-15T20:00"), ANY_GROUP,
                    List.of(weekly, myPack, someoneElses));

            // Assert
            assertThat(reason).isEmpty();
        }

        @Test
        void rejectsANullMemberSubscriptionCollection() {
            // Arrange
            Subscription subscription = subscribe(perWeek(1), "2026-10-01");
            Executable rejection = () -> subscription.rejectionFor(lisbon("2026-10-14T20:00"), ANY_GROUP, null);
            Executable consume = () -> subscription.consume(BookingId.generate(), lisbon("2026-10-14T20:00"), null);
            Executable balance = () -> subscription.balanceOn(LocalDate.parse("2026-10-14"), null);

            // Act
            NullPointerException onRejection = assertThrows(NullPointerException.class, rejection);
            NullPointerException onConsume = assertThrows(NullPointerException.class, consume);
            NullPointerException onBalance = assertThrows(NullPointerException.class, balance);

            // Assert
            assertThat(onRejection.getMessage()).contains("memberSubscriptions");
            assertThat(onConsume.getMessage()).contains("memberSubscriptions");
            assertThat(onBalance.getMessage()).contains("memberSubscriptions");
        }

        @Test
        void reconstructRejectsAWeekWithMoreSessionsThanAllowed() {
            // Arrange
            List<CreditUsage> sameWeek = List.of(
                    new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-12")),
                    new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-18")));
            Executable act = () -> Subscription.reconstruct(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), PlanTerms.monthlyNPerWeek(1, Set.of()), LocalDate.parse("2026-10-01"),
                    LocalDate.parse("2026-10-31"), PaymentStatus.PAID, sameWeek);

            // Act
            InvalidSubscriptionException ex = assertThrows(InvalidSubscriptionException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("week");
        }

        @Test
        void reconstructAcceptsTheAllowanceInEachOfTwoAdjacentWeeks() {
            // Arrange
            List<CreditUsage> adjacentWeeks = List.of(
                    new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-18")),
                    new CreditUsage(BookingId.generate(), LocalDate.parse("2026-10-19")));

            // Act
            Subscription subscription = Subscription.reconstruct(SubscriptionId.generate(), ASSOCIATION, MEMBER,
                    PlanId.generate(), PlanTerms.monthlyNPerWeek(1, Set.of()), LocalDate.parse("2026-10-01"),
                    LocalDate.parse("2026-10-31"), PaymentStatus.PAID, adjacentWeeks);

            // Assert
            assertThat(subscription.usages()).hasSize(2);
        }
    }

    @Nested
    class RefundForBooking {

        private final SessionId session = SessionId.generate();

        private Booking booking(BookingId id, MemberId member, AssociationId association, BookingStatus status,
                                boolean confirmed, CancellationKind kind) {
            Instant requestedAt = Instant.parse("2026-10-10T10:00:00Z");
            return Booking.reconstruct(id, association, session, member, status, requestedAt,
                    confirmed ? requestedAt.plusSeconds(60) : null, kind);
        }

        static Stream<Arguments> cases() {
            return Stream.of(
                    Arguments.of("free cancellation of a confirmed booking", BookingStatus.CANCELLED, true, CancellationKind.FREE, true),
                    Arguments.of("session cancellation of a confirmed booking", BookingStatus.CANCELLED, true, CancellationKind.BY_SESSION, true),
                    Arguments.of("session cancellation of a waitlisted booking", BookingStatus.CANCELLED, false, CancellationKind.BY_SESSION, false),
                    Arguments.of("free cancellation of a waitlisted booking", BookingStatus.CANCELLED, false, CancellationKind.FREE, false),
                    Arguments.of("late cancellation", BookingStatus.CANCELLED, true, CancellationKind.LATE, false),
                    Arguments.of("no-show", BookingStatus.NO_SHOW, true, null, false),
                    Arguments.of("attended", BookingStatus.ATTENDED, true, null, false),
                    Arguments.of("still confirmed", BookingStatus.CONFIRMED, true, null, false)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void refundsOnlyWhenTheBookingsCancellationEarnsIt(String description, BookingStatus status, boolean confirmed,
                                                           CancellationKind kind, boolean refunded) {
            // Arrange
            BookingId id = BookingId.generate();
            Subscription charged = subscribe(pack(5, 90), "2026-10-01").consume(id, lisbon("2026-10-20T20:00"));
            Booking booking = booking(id, MEMBER, ASSOCIATION, status, confirmed, kind);

            // Act
            Subscription result = charged.refundFor(booking);

            // Assert
            assertThat(result.holdsPlaceFor(id)).isEqualTo(!refunded);
            assertThat(result.creditsUsed()).isEqualTo(refunded ? 0 : 1);
        }

        @Test
        void aRefundableBookingThatNeverConsumedIsANoOp() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");
            Booking booking = booking(BookingId.generate(), MEMBER, ASSOCIATION, BookingStatus.CANCELLED, true,
                    CancellationKind.FREE);

            // Act
            Subscription result = subscription.refundFor(booking);

            // Assert
            assertThat(result).isSameAs(subscription);
        }

        @Test
        void refusesAnotherMembersBooking() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");
            Booking booking = booking(BookingId.generate(), MemberId.generate(), ASSOCIATION, BookingStatus.CANCELLED,
                    true, CancellationKind.FREE);
            Executable act = () -> subscription.refundFor(booking);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("member");
        }

        @Test
        void refusesAnotherAssociationsBooking() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");
            Booking booking = booking(BookingId.generate(), MEMBER, AssociationId.generate(), BookingStatus.CANCELLED,
                    true, CancellationKind.FREE);
            Executable act = () -> subscription.refundFor(booking);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("member");
        }

        @Test
        void rejectsANullBooking() {
            // Arrange
            Subscription subscription = subscribe(pack(5, 90), "2026-10-01");
            Executable act = () -> subscription.refundFor(null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("booking");
        }

        @Test
        void findsTheSubscriptionHoldingTheBookingsPlace() {
            // Arrange
            BookingId id = BookingId.generate();
            Subscription old = subscribe(pack(5, 10), "2026-09-01");
            Subscription holder = subscribe(pack(5, 90), "2026-10-01").consume(id, lisbon("2026-10-20T20:00"));

            // Act
            Optional<Subscription> found = Subscription.holdingPlaceFor(id, List.of(old, holder));
            Optional<Subscription> none = Subscription.holdingPlaceFor(BookingId.generate(), List.of(old, holder));

            // Assert
            assertThat(found).contains(holder);
            assertThat(none).isEmpty();
        }

        @Test
        void holdingPlaceForRejectsNulls() {
            // Arrange
            Executable noId = () -> Subscription.holdingPlaceFor(null, List.of());
            Executable noList = () -> Subscription.holdingPlaceFor(BookingId.generate(), null);

            // Act
            NullPointerException onId = assertThrows(NullPointerException.class, noId);
            NullPointerException onList = assertThrows(NullPointerException.class, noList);

            // Assert
            assertThat(onId.getMessage()).contains("bookingId");
            assertThat(onList.getMessage()).contains("subscriptions");
        }
    }

    @Test
    void creatingRejectsExistingSubscriptionsOfAnotherAssociation() {
        // Arrange
        Plan otherPlan = Plan.create(AssociationId.generate(), "Other", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);
        Subscription foreign = Subscription.create(otherPlan, MemberId.generate(), LocalDate.parse("2026-10-01"), List.of());
        Executable act = () -> Subscription.create(unlimited(), MEMBER, LocalDate.parse("2026-10-01"), List.of(foreign));

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("association");
    }

    @Test
    void comparesById() {
        // Arrange
        Subscription first = subscribe(unlimited(), "2026-10-01");
        Subscription sameId = first.markPaid();
        Subscription other = subscribe(unlimited(), "2026-10-01");

        // Act
        boolean sameIdEqual = first.equals(sameId);
        boolean otherEqual = first.equals(other);

        // Assert
        assertThat(sameIdEqual).isTrue();
        assertThat(first).hasSameHashCodeAs(sameId);
        assertThat(otherEqual).isFalse();
        assertThat(first).isNotEqualTo("a subscription");
        assertThat(first.toString()).contains(first.id().toString()).contains("PENDING");
    }

    @Test
    void exposesNoMutableUsageList() {
        // Arrange
        Subscription subscription = consumeAll(subscribe(unlimited(), "2026-10-01"), "2026-10-14T20:00");
        List<CreditUsage> usages = new ArrayList<>(subscription.usages());

        // Act
        usages.clear();

        // Assert
        assertThat(subscription.usages()).hasSize(1);
        assertThrows(UnsupportedOperationException.class, () -> subscription.usages().clear());
    }
}
