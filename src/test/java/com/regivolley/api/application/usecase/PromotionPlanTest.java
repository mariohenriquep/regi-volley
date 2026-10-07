package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.service.BookingTarget;
import com.regivolley.api.domain.service.MemberBookingProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromotionPlanTest {

    private Association association;
    private TrainingGroup group;
    private Session session;
    private Member waiting;
    private Booking waitingBooking;
    private BookingTarget target;

    @BeforeEach
    void setUp() {
        association = Data.association();
        group = Data.group(association, Data.coach(association), "Beginner");
        waiting = Data.member(association);
        session = Data.booked(Data.booked(Data.session(group, 1), Data.member(association), Data.NOW.minusSeconds(100)),
                waiting, Data.NOW.minusSeconds(50));
        waitingBooking = Data.bookingOf(session, waiting);
        target = BookingTarget.of(session, association.ranksOf(group.acceptedLevels()));
    }

    private WaitlistPromotionPlan planWith(Subscription... subscriptions) {
        MemberBookingProfile profile = waiting.bookingProfile(association, List.of(subscriptions));
        return new WaitlistPromotionPlan(target, Map.of(waiting.id(), profile));
    }

    @Test
    void nobodyWaitingSelectsNobodyAndChargesNothing() {
        // Arrange
        PromotionPlan plan = PromotionPlan.nobodyWaiting();

        // Act
        boolean eligible = plan.eligibility().test(MemberId.generate());
        List<Subscription> charges = plan.charge(List.of(waitingBooking));

        // Assert
        assertThat(eligible).isFalse();
        assertThat(charges).isEmpty();
    }

    @Test
    void selectsOnlyWaitingMembersWhoAreEligible() {
        // Arrange
        WaitlistPromotionPlan eligible = planWith(Data.unlimited(association, waiting));
        WaitlistPromotionPlan broke = planWith();

        // Act
        boolean withPlan = eligible.eligibility().test(waiting.id());
        boolean withoutPlan = broke.eligibility().test(waiting.id());
        boolean stranger = eligible.eligibility().test(MemberId.generate());

        // Assert
        assertThat(withPlan).isTrue();
        assertThat(withoutPlan).isFalse();
        assertThat(stranger).isFalse();
    }

    @Test
    void chargesThePromotedBookingOnTheChosenSubscriptionWithoutSavingAnything() {
        // Arrange
        Subscription pack = Data.pack(association, waiting, 10);
        WaitlistPromotionPlan plan = planWith(pack);
        Booking promoted = promotedBookingOf(waiting);

        // Act
        List<Subscription> charges = plan.charge(List.of(promoted));

        // Assert
        assertThat(charges).singleElement().satisfies(charged -> {
            assertThat(charged.id()).isEqualTo(pack.id());
            assertThat(charged.holdsPlaceFor(promoted.id())).isTrue();
        });
        assertThat(pack.holdsPlaceFor(promoted.id())).isFalse();
    }

    @Test
    void aBookingThatAlreadyHoldsItsPlaceNeedsNoSave() {
        // Arrange
        Booking promoted = promotedBookingOf(waiting);
        Subscription alreadyCharged = Data.charged(Data.pack(association, waiting, 10), session, promoted);
        WaitlistPromotionPlan plan = planWith(alreadyCharged);

        // Act
        List<Subscription> charges = plan.charge(List.of(promoted));

        // Assert
        assertThat(charges).isEmpty();
    }

    @Test
    void refusesToChargeSomeoneWhoStoppedBeingEligible() {
        // Arrange
        WaitlistPromotionPlan plan = planWith();
        Executable act = () -> plan.charge(List.of(promotedBookingOf(waiting)));

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isNotNull();
    }

    private Booking promotedBookingOf(Member member) {
        Session freed = session.cancelBooking(
                session.bookings().stream().filter(b -> !b.memberId().equals(member.id())).findFirst().orElseThrow().id(),
                Data.POLICY, Data.CLOCK, id -> true).session();
        return Data.bookingOf(freed, member);
    }
}
