package com.regivolley.api.domain.service;

import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.LevelRank;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Integration of the Association/Member model (issue #17) with booking eligibility (issue #13). */
class MemberBookingEligibilityTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
    private static final MemberId COACH = MemberId.generate();

    private static final Association ASSOCIATION = AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co",
            List.of("Beginner", "Intermediate", "Advanced"));
    private static final Level BEGINNER = ASSOCIATION.levels().get(0);
    private static final Level INTERMEDIATE = ASSOCIATION.levels().get(1);
    private static final Level ADVANCED = ASSOCIATION.levels().get(2);

    // 20:00 Lisbon (WEST) on Wednesday 14 Oct 2026
    private static final Instant SESSION_START = Instant.parse("2026-10-14T19:00:00Z");

    private static Member newMember() {
        JoinRequest request = JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of("Ana Silva", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-10", CLOCK), CLOCK);
        return MemberFactory.fromApprovedJoinRequest(ASSOCIATION, request.approve(COACH, CLOCK));
    }

    private static Subscription octoberUnlimited(Member member) {
        Plan plan = PlanFactory.create(ASSOCIATION.id(), "Unlimited", PlanTerms.monthlyUnlimited(Set.of()),
                Money.ofCents(3500), null);
        return SubscriptionFactory.create(plan, member.id(), LocalDate.parse("2026-10-01"), List.of());
    }

    private static BookingTarget groupAccepting(Level... levels) {
        return new BookingTarget(ASSOCIATION.id(), SESSION_START,
                Set.of(java.util.Arrays.stream(levels).map(Level::toRank).toArray(LevelRank[]::new)));
    }

    @Nested
    class Profile {

        @Test
        void carriesTheMembersStatusLevelRankAndSubscriptions() {
            // Arrange
            Member member = newMember().changeLevel(ASSOCIATION, INTERMEDIATE.id(), COACH, CLOCK);
            Subscription subscription = octoberUnlimited(member);

            // Act
            MemberBookingProfile profile = member.bookingProfile(ASSOCIATION, List.of(subscription));

            // Assert
            assertThat(profile.associationId()).isEqualTo(ASSOCIATION.id());
            assertThat(profile.memberId()).isEqualTo(member.id());
            assertThat(profile.status()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(profile.level()).isEqualTo(new LevelRank(INTERMEDIATE.id(), 1));
            assertThat(profile.subscriptions()).containsExactly(subscription);
        }

        @Test
        void rejectsAnAssociationThatIsNotTheMembers() {
            // Arrange
            Association other = AssociationFactory.create("Other", "other", null, "Porto", "x@y.co", List.of("Open"));
            Member member = newMember();
            Executable act = () -> member.bookingProfile(other, List.of());

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("another association");
        }

        @Test
        void rejectsSubscriptionsOfAnotherMember() {
            // Arrange
            Member member = newMember();
            Subscription someoneElses = octoberUnlimited(newMember());
            Executable act = () -> member.bookingProfile(ASSOCIATION, List.of(someoneElses));

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("own subscriptions");
        }
    }

    @Nested
    class Eligibility {

        @Test
        void aNewlyApprovedMemberMayBookAGroupAtTheEntryLevel() {
            // Arrange
            Member member = newMember();
            MemberBookingProfile profile = member.bookingProfile(ASSOCIATION, List.of(octoberUnlimited(member)));

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(profile, groupAccepting(BEGINNER));

            // Assert
            assertThat(decision.isEligible()).isTrue();
        }

        @Test
        void aNewlyApprovedMemberMayNotBookAHigherLevelGroup() {
            // Arrange
            Member member = newMember();
            MemberBookingProfile profile = member.bookingProfile(ASSOCIATION, List.of(octoberUnlimited(member)));

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(profile, groupAccepting(INTERMEDIATE, ADVANCED));

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.LEVEL_NOT_ALLOWED);
        }

        @Test
        void aLevelChangeAppliesToTheNextBooking() {
            // Arrange
            Member beginner = newMember();
            Subscription subscription = octoberUnlimited(beginner);
            BookingTarget target = groupAccepting(INTERMEDIATE, ADVANCED);
            Member promoted = beginner.changeLevel(ASSOCIATION, INTERMEDIATE.id(), COACH, CLOCK);

            // Act
            EligibilityDecision before = BookingEligibility.evaluate(
                    beginner.bookingProfile(ASSOCIATION, List.of(subscription)), target);
            EligibilityDecision after = BookingEligibility.evaluate(
                    promoted.bookingProfile(ASSOCIATION, List.of(subscription)), target);

            // Assert
            assertThat(before.isEligible()).isFalse();
            assertThat(after.isEligible()).isTrue();
        }

        @Test
        void aDeactivatedMemberIsRejected() {
            // Arrange
            Member member = newMember();
            Subscription subscription = octoberUnlimited(member);
            Member inactive = member.deactivate();

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(
                    inactive.bookingProfile(ASSOCIATION, List.of(subscription)), groupAccepting(BEGINNER));

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.MEMBER_INACTIVE);
        }

        @Test
        void anAnonymisedMemberIsRejected() {
            // Arrange
            Member member = newMember();
            Subscription subscription = octoberUnlimited(member);
            Member erased = member.anonymise(CLOCK);

            // Act
            EligibilityDecision decision = BookingEligibility.evaluate(
                    erased.bookingProfile(ASSOCIATION, List.of(subscription)), groupAccepting(BEGINNER));

            // Assert
            assertThat(decision.rejection()).contains(BookingRejectionReason.MEMBER_INACTIVE);
        }

        @Test
        void reorderingLevelsChangesWhoMayBook() {
            // Arrange
            Member member = newMember();
            Subscription subscription = octoberUnlimited(member);
            Association reversed = ASSOCIATION.reorderLevels(List.of(ADVANCED.id(), INTERMEDIATE.id(), BEGINNER.id()));
            BookingTarget advancedGroupBefore = groupAccepting(ADVANCED);
            BookingTarget advancedGroupAfter = new BookingTarget(ASSOCIATION.id(), SESSION_START,
                    Set.of(reversed.rankOf(ADVANCED.id())));

            // Act
            EligibilityDecision before = BookingEligibility.evaluate(
                    member.bookingProfile(ASSOCIATION, List.of(subscription)), advancedGroupBefore);
            EligibilityDecision after = BookingEligibility.evaluate(
                    member.bookingProfile(reversed, List.of(subscription)), advancedGroupAfter);

            // Assert
            assertThat(before.rejection()).contains(BookingRejectionReason.LEVEL_NOT_ALLOWED);
            assertThat(after.isEligible()).isTrue();
        }
    }
}
