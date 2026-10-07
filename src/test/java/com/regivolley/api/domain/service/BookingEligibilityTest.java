package com.regivolley.api.domain.service;

import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.result.BookingCancellation;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.LevelRank;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingEligibilityTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();
    private static final Money PRICE = Money.ofCents(3500);

    private static final LevelRank BEGINNER = new LevelRank(LevelId.generate(), 0);
    private static final LevelRank INTERMEDIATE = new LevelRank(LevelId.generate(), 1);
    private static final LevelRank ADVANCED = new LevelRank(LevelId.generate(), 2);

    // 20:00 Lisbon (WEST) on Wednesday 14 Oct 2026
    private static final Instant SESSION_START = Instant.parse("2026-10-14T19:00:00Z");
    /** Open play: accepts Intermediate and Advanced (RN-21). */
    private static final BookingTarget OPEN_PLAY = new BookingTarget(ASSOCIATION, SESSION_START, Set.of(INTERMEDIATE, ADVANCED));

    private static Subscription subscription(MemberId member, PlanTerms terms, Integer validityDays, String start) {
        Plan plan = Plan.create(ASSOCIATION, "Plan", terms, PRICE, validityDays);
        return Subscription.create(plan, member, LocalDate.parse(start), List.of());
    }

    private static Subscription octoberUnlimited(MemberId member) {
        return subscription(member, PlanTerms.monthlyUnlimited(Set.of()), null, "2026-10-01");
    }

    private static MemberBookingProfile profile(MemberId member, MemberStatus status, LevelRank level,
                                                Subscription... subscriptions) {
        return new MemberBookingProfile(ASSOCIATION, member, status, level, List.of(subscriptions));
    }

    private static MemberBookingProfile activeIntermediate(MemberId member, Subscription... subscriptions) {
        return profile(member, MemberStatus.ACTIVE, INTERMEDIATE, subscriptions);
    }

    @Nested
    class Eligible {

        @Test
        void anActiveMemberWithAValidSubscriptionAtAnAcceptedLevelMayBook() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription subscription = octoberUnlimited(member);

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, subscription), OPEN_PLAY);

            // Assert
            assertThat(decision.isEligible()).isTrue();
            assertThat(decision.subscriptionToCharge()).contains(subscription);
            assertThat(decision.rejection()).isEmpty();
            assertThat(decision.requireEligible()).isSameAs(subscription);
            assertThat(decision.toString()).startsWith("ELIGIBLE");
        }

        @Test
        void aPendingSubscriptionMayBook() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription pending = octoberUnlimited(member);

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, pending), OPEN_PLAY);

            // Assert
            assertThat(pending.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(decision.isEligible()).isTrue();
        }

        @Test
        void aPaidSubscriptionMayBook() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription paid = octoberUnlimited(member).markPaid();

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, paid), OPEN_PLAY);

            // Assert
            assertThat(decision.isEligible()).isTrue();
        }

        @Test
        void aHigherLevelMemberMayBookALowerLevelGroup() {
            // Arrange
            MemberId member = MemberId.generate();
            BookingTarget beginnersGroup = new BookingTarget(ASSOCIATION, SESSION_START, Set.of(BEGINNER));
            MemberBookingProfile advanced = profile(member, MemberStatus.ACTIVE, ADVANCED, octoberUnlimited(member));

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(advanced, beginnersGroup);

            // Assert
            assertThat(decision.isEligible()).isTrue();
        }

        @Test
        void aPlanRestrictedToOneOfTheGroupsLevelsMayBook() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription restricted = subscription(member, PlanTerms.pack(5, Set.of(ADVANCED.levelId())), 90, "2026-10-01");

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, restricted), OPEN_PLAY);

            // Assert
            assertThat(decision.isEligible()).isTrue();
        }

        @Test
        void chargesTheEarliestStartingUsableSubscription() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription later = subscription(member, PlanTerms.monthlyUnlimited(Set.of()), null, "2026-10-10");
            Subscription earlier = subscription(member, PlanTerms.monthlyUnlimited(Set.of()), null, "2026-10-01");

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, later, earlier), OPEN_PLAY);

            // Assert
            assertThat(decision.subscriptionToCharge()).contains(earlier);
        }

        @Test
        void skipsAnUnusableSubscriptionForAUsableOne() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription overdue = octoberUnlimited(member).markOverdue();
            Subscription usable = subscription(member, PlanTerms.pack(5, Set.of()), 90, "2026-10-05");

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, overdue, usable), OPEN_PLAY);

            // Assert
            assertThat(decision.subscriptionToCharge()).contains(usable);
        }
    }

    @Nested
    class Rejected {

        @ParameterizedTest
        @EnumSource(value = MemberStatus.class, names = "INACTIVE")
        void rejectsAnInactiveMember(MemberStatus status) {
            // Arrange
            MemberId member = MemberId.generate();
            MemberBookingProfile inactive = profile(member, status, ADVANCED, octoberUnlimited(member));

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(inactive, OPEN_PLAY);

            // Assert
            assertThat(decision.isEligible()).isFalse();
            assertThat(decision.rejection()).contains(BookingRejectionReason.MEMBER_INACTIVE);
            assertThat(decision.subscriptionToCharge()).isEmpty();
            assertThat(decision.toString()).isEqualTo("REJECTED(MEMBER_INACTIVE)");
        }

        @Test
        void rejectsAMemberWhoseLevelIsBelowEveryAcceptedLevel() {
            // Arrange
            MemberId member = MemberId.generate();
            MemberBookingProfile beginner = profile(member, MemberStatus.ACTIVE, BEGINNER, octoberUnlimited(member));

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(beginner, OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.LEVEL_NOT_ALLOWED);
        }

        @Test
        void rejectsAPlanThatDoesNotGiveAccessToTheGroupsLevels() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription beginnersOnly = subscription(member, PlanTerms.pack(5, Set.of(BEGINNER.levelId())), 90, "2026-10-01");

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, beginnersOnly), OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.PLAN_LEVEL_NOT_ALLOWED);
        }

        @Test
        void rejectsAMemberWithoutAnySubscription() {
            // Arrange
            MemberBookingProfile noPlan = activeIntermediate(MemberId.generate());

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(noPlan, OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }

        @Test
        void rejectsWhenNoSubscriptionCoversTheSessionDate() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription september = subscription(member, PlanTerms.monthlyUnlimited(Set.of()), null, "2026-09-01");
            Subscription november = subscription(member, PlanTerms.monthlyUnlimited(Set.of()), null, "2026-11-01");

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, september, november), OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }

        @Test
        void rejectsAnExhaustedPack() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription exhausted = subscription(member, PlanTerms.pack(1, Set.of()), 90, "2026-10-01")
                    .consume(BookingId.generate(), SESSION_START.minus(Duration.ofDays(3)));

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, exhausted), OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.NO_BALANCE);
        }

        @Test
        void rejectsAnOverdueSubscriptionEvenWithBalance() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription overdue = octoberUnlimited(member).markOverdue();

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, overdue), OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.PAYMENT_OVERDUE);
        }

        @Test
        void reportsTheReasonClosestToBookableWhenSeveralSubscriptionsAreRejected() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription expired = subscription(member, PlanTerms.monthlyUnlimited(Set.of()), null, "2026-09-01");
            Subscription overdue = octoberUnlimited(member).markOverdue();

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(activeIntermediate(member, expired, overdue), OPEN_PLAY);

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.PAYMENT_OVERDUE);
        }

        @Test
        void checksTheMemberBeforeTheLevelAndTheLevelBeforeThePlan() {
            // Arrange
            MemberId member = MemberId.generate();
            MemberBookingProfile inactiveBeginnerWithNoPlan = profile(member, MemberStatus.INACTIVE, BEGINNER);
            MemberBookingProfile activeBeginnerWithNoPlan = profile(member, MemberStatus.ACTIVE, BEGINNER);

            // Act
            EligibilityDecision inactive = BookingEligibility.evaluate(inactiveBeginnerWithNoPlan, OPEN_PLAY);
            EligibilityDecision level = BookingEligibility.evaluate(activeBeginnerWithNoPlan, OPEN_PLAY);

            // Assert
            assertThat(inactive.rejection()).contains(BookingRejectionReason.MEMBER_INACTIVE);
            assertThat(level.rejection()).contains(BookingRejectionReason.LEVEL_NOT_ALLOWED);
        }

        @ParameterizedTest
        @EnumSource(BookingRejectionReason.class)
        void requireEligibleThrowsTheStructuredReasonWithAMessage(BookingRejectionReason reason) {
            // Arrange
            EligibilityDecision decision = EligibilityDecision.rejected(reason);
            Executable act = decision::requireEligible;

            // Act
            BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

            // Assert
            assertThat(ex.reason()).isEqualTo(reason);
            assertThat(ex.getMessage()).isNotBlank();
        }

        @Test
        void everyReasonHasADistinctMessage() {
            // Arrange
            List<BookingRejectionReason> reasons = List.of(BookingRejectionReason.values());

            // Act
            List<String> messages = reasons.stream().map(r -> new BookingNotAllowedException(r).getMessage()).toList();

            // Assert
            assertThat(messages).doesNotHaveDuplicates();
        }

        @Test
        void rejectsNullArguments() {
            // Arrange
            MemberBookingProfile profile = activeIntermediate(MemberId.generate());
            Executable noProfile = () -> BookingEligibility.evaluate(null, OPEN_PLAY);
            Executable noTarget = () -> BookingEligibility.evaluate(profile, null);

            // Act
            NullPointerException onProfile = assertThrows(NullPointerException.class, noProfile);
            NullPointerException onTarget = assertThrows(NullPointerException.class, noTarget);

            // Assert
            assertThat(onProfile.getMessage()).contains("profile");
            assertThat(onTarget.getMessage()).contains("target");
        }
    }

    @Nested
    class InputValidation {

        @Test
        void aTargetNeedsAtLeastOneAcceptedLevel() {
            // Arrange
            Executable act = () -> new BookingTarget(ASSOCIATION, SESSION_START, Set.of());

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one level");
        }

        @Test
        void aTargetExposesTheAcceptedLevelIds() {
            // Arrange
            BookingTarget target = new BookingTarget(ASSOCIATION, SESSION_START, Set.of(INTERMEDIATE, ADVANCED));

            // Act
            Set<LevelId> ids = target.acceptedLevelIds();

            // Assert
            assertThat(ids).containsExactlyInAnyOrder(INTERMEDIATE.levelId(), ADVANCED.levelId());
        }

        @Test
        void aTargetCanBeBuiltFromASession() {
            // Arrange
            Session session = Session.create(ASSOCIATION, TrainingGroupId.generate(), MemberId.generate(),
                    SESSION_START, SESSION_START.plus(Duration.ofMinutes(90)), 12);

            // Act
            BookingTarget target = BookingTarget.of(session, Set.of(ADVANCED));

            // Assert
            assertThat(target.associationId()).isEqualTo(ASSOCIATION);
            assertThat(target.sessionStart()).isEqualTo(SESSION_START);
            assertThat(target.acceptedLevels()).containsExactly(ADVANCED);
        }

        @Test
        void aProfileCannotHoldAnotherMembersSubscription() {
            // Arrange
            MemberId member = MemberId.generate();
            Subscription someoneElses = octoberUnlimited(MemberId.generate());
            Executable act = () -> activeIntermediate(member, someoneElses);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("own subscriptions");
        }
    }

    @Nested
    class WeeklyAllowancePerMember {

        @Test
        void aMidWeekRenewalSharesTheWeeklyLimitWithThePreviousSubscription() {
            // Arrange
            MemberId member = MemberId.generate();
            Plan weekly = Plan.create(ASSOCIATION, "Twice a week", PlanTerms.monthlyNPerWeek(2, Set.of()), PRICE, null);
            Subscription previous = Subscription.create(weekly, member, LocalDate.parse("2026-09-16"), List.of())
                    .consume(BookingId.generate(), Instant.parse("2026-10-12T19:00:00Z"))
                    .consume(BookingId.generate(), Instant.parse("2026-10-13T19:00:00Z"));
            Subscription renewal = Subscription.renew(weekly, previous, List.of(previous), LocalDate.parse("2026-10-14"));
            MemberBookingProfile profile = activeIntermediate(member, previous, renewal);
            BookingTarget saturday = new BookingTarget(ASSOCIATION, Instant.parse("2026-10-17T19:00:00Z"), Set.of(INTERMEDIATE));
            BookingTarget nextMonday = new BookingTarget(ASSOCIATION, Instant.parse("2026-10-19T19:00:00Z"), Set.of(INTERMEDIATE));

            // Act
            EligibilityDecision sameWeek = BookingEligibility.evaluate(profile, saturday);
            EligibilityDecision nextWeek = BookingEligibility.evaluate(profile, nextMonday);

            // Assert
            assertThat(renewal.startDate()).isEqualTo(LocalDate.parse("2026-10-16"));
            assertThat(sameWeek.rejection()).contains(BookingRejectionReason.NO_BALANCE);
            assertThat(nextWeek.subscriptionToCharge()).contains(renewal);
        }

        @Test
        void consumingTheChosenSubscriptionWithTheProfilesSubscriptionsAgreesWithEligibility() {
            // Arrange
            MemberId member = MemberId.generate();
            Plan weekly = Plan.create(ASSOCIATION, "Twice a week", PlanTerms.monthlyNPerWeek(2, Set.of()), PRICE, null);
            Subscription previous = Subscription.create(weekly, member, LocalDate.parse("2026-09-16"), List.of())
                    .consume(BookingId.generate(), Instant.parse("2026-10-13T19:00:00Z"));
            Subscription renewal = Subscription.renew(weekly, previous, List.of(previous), LocalDate.parse("2026-10-14"));
            MemberBookingProfile profile = activeIntermediate(member, previous, renewal);
            BookingTarget saturday = new BookingTarget(ASSOCIATION, Instant.parse("2026-10-17T19:00:00Z"), Set.of(INTERMEDIATE));

            // Act
            Subscription charged = BookingEligibility.evaluate(profile, saturday).requireEligible()
                    .consume(BookingId.generate(), saturday.sessionStart(), profile.subscriptions());
            Executable second = () -> charged.consume(BookingId.generate(), saturday.sessionStart(),
                    List.of(previous, charged));

            // Assert
            assertThat(charged.creditsUsed()).isEqualTo(1);
            assertThat(assertThrows(BookingNotAllowedException.class, second).reason())
                    .isEqualTo(BookingRejectionReason.NO_BALANCE);
        }
    }

    @Nested
    class TenantGuard {

        private static final AssociationId OTHER = AssociationId.generate();

        @Test
        void rejectsAProfileOfAnotherAssociation() {
            // Arrange
            MemberId member = MemberId.generate();
            MemberBookingProfile foreign = new MemberBookingProfile(OTHER, member, MemberStatus.ACTIVE, ADVANCED, List.of());
            Executable act = () -> BookingEligibility.evaluate(foreign, OPEN_PLAY);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("different associations");
        }

        @Test
        void aProfileCannotHoldASubscriptionOfAnotherAssociation() {
            // Arrange
            MemberId member = MemberId.generate();
            Plan foreignPlan = Plan.create(OTHER, "Other", PlanTerms.monthlyUnlimited(Set.of()), PRICE, null);
            Subscription foreign = Subscription.create(foreignPlan, member, LocalDate.parse("2026-10-01"), List.of());
            Executable act = () -> activeIntermediate(member, foreign);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("own association");
        }

        @Test
        void aTargetCapturesTheSessionsAssociation() {
            // Arrange
            Session session = Session.create(OTHER, TrainingGroupId.generate(), MemberId.generate(), SESSION_START,
                    SESSION_START.plus(Duration.ofMinutes(90)), 12);

            // Act
            BookingTarget target = BookingTarget.of(session, Set.of(ADVANCED));

            // Assert
            assertThat(target.associationId()).isEqualTo(OTHER);
        }

        @Test
        void promotionFilterRejectsAProfileOfAnotherAssociation() {
            // Arrange
            MemberBookingProfile foreign = new MemberBookingProfile(OTHER, MemberId.generate(), MemberStatus.ACTIVE,
                    ADVANCED, List.of());
            Executable act = () -> BookingEligibility.promotionFilter(List.of(foreign), OPEN_PLAY);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("different associations");
        }

        @Test
        void promotionFilterRejectsTwoProfilesOfTheSameMember() {
            // Arrange
            MemberId member = MemberId.generate();
            Executable act = () -> BookingEligibility.promotionFilter(
                    List.of(activeIntermediate(member), activeIntermediate(member)), OPEN_PLAY);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("Duplicate profile").contains(member.toString());
        }

        @Test
        void aTargetNeedsAnAssociation() {
            // Arrange
            Executable act = () -> new BookingTarget(null, SESSION_START, Set.of(ADVANCED));

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("associationId");
        }
    }

    @Nested
    class RejectionPriority {

        @Test
        void everyReasonHasADistinctPriorityAndTheIntendedOrder() {
            // Arrange
            List<BookingRejectionReason> inOrder = List.of(
                    BookingRejectionReason.MEMBER_INACTIVE, BookingRejectionReason.LEVEL_NOT_ALLOWED,
                    BookingRejectionReason.NO_VALID_SUBSCRIPTION, BookingRejectionReason.PLAN_LEVEL_NOT_ALLOWED,
                    BookingRejectionReason.NO_BALANCE, BookingRejectionReason.PAYMENT_OVERDUE);

            // Act
            List<Integer> priorities = inOrder.stream().map(BookingRejectionReason::priority).toList();

            // Assert
            assertThat(priorities).isSorted().doesNotHaveDuplicates();
            assertThat(BookingRejectionReason.values()).containsExactlyInAnyOrderElementsOf(inOrder);
        }

        @Test
        void aReasonIsCloserToBookableThanALowerPriorityOneOnly() {
            // Arrange
            BookingRejectionReason overdue = BookingRejectionReason.PAYMENT_OVERDUE;
            BookingRejectionReason expired = BookingRejectionReason.NO_VALID_SUBSCRIPTION;

            // Act
            boolean overdueCloser = overdue.isCloserToBookableThan(expired);
            boolean expiredCloser = expired.isCloserToBookableThan(overdue);
            boolean sameCloser = overdue.isCloserToBookableThan(overdue);

            // Assert
            assertThat(overdueCloser).isTrue();
            assertThat(expiredCloser).isFalse();
            assertThat(sameCloser).isFalse();
        }
    }

    // -------------------------------------------------------------------- RN-09 seam

    @Nested
    class PromotionSeam {

        private static final BookingPolicy POLICY = BookingPolicy.defaults();

        @Test
        void aMemberIsPromotableWhenEligibleAndNotOtherwise() {
            // Arrange
            MemberId eligible = MemberId.generate();
            MemberId overdue = MemberId.generate();
            MemberId unknown = MemberId.generate();
            Predicate<MemberId> filter = BookingEligibility.promotionFilter(List.of(
                    activeIntermediate(eligible, octoberUnlimited(eligible)),
                    activeIntermediate(overdue, octoberUnlimited(overdue).markOverdue())), OPEN_PLAY);

            // Act
            boolean eligibleResult = filter.test(eligible);
            boolean overdueResult = filter.test(overdue);
            boolean unknownResult = filter.test(unknown);

            // Assert
            assertThat(eligibleResult).isTrue();
            assertThat(overdueResult).isFalse();
            assertThat(unknownResult).isFalse();
        }

        @Test
        void rejectsANullTarget() {
            // Arrange
            Executable act = () -> BookingEligibility.promotionFilter(List.of(), null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("target");
        }

        @Test
        void skipsAnOverdueWaitlistedMemberWhenAFreedSeatIsPromoted() {
            // Arrange
            MemberId coach = MemberId.generate();
            MemberId holder = MemberId.generate();
            MemberId overdueFirst = MemberId.generate();
            MemberId paidSecond = MemberId.generate();
            Session session = Session.create(ASSOCIATION, TrainingGroupId.generate(), coach, SESSION_START,
                    SESSION_START.plus(Duration.ofMinutes(90)), 1);
            Instant opensAt = POLICY.bookingOpensAt(SESSION_START);
            session = session.book(holder, POLICY, Clock.fixed(opensAt, ZoneOffset.UTC)).session();
            session = session.book(overdueFirst, POLICY, Clock.fixed(opensAt.plusSeconds(1), ZoneOffset.UTC)).session();
            session = session.book(paidSecond, POLICY, Clock.fixed(opensAt.plusSeconds(2), ZoneOffset.UTC)).session();
            BookingId holderBooking = session.bookings().stream()
                    .filter(b -> b.memberId().equals(holder)).findFirst().orElseThrow().id();
            Predicate<MemberId> filter = BookingEligibility.promotionFilter(List.of(
                    activeIntermediate(overdueFirst, octoberUnlimited(overdueFirst).markOverdue()),
                    activeIntermediate(paidSecond, octoberUnlimited(paidSecond).markPaid())), OPEN_PLAY);

            // Act
            BookingCancellation cancellation = session.cancelBooking(holderBooking, POLICY,
                    Clock.fixed(opensAt.plusSeconds(10), ZoneOffset.UTC), filter);

            // Assert
            assertThat(cancellation.promoted()).extracting(Booking::memberId).containsExactly(paidSecond);
        }
    }
}
