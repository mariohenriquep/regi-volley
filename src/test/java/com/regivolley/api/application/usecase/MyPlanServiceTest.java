package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MyPlanQuery;
import com.regivolley.api.application.result.MyPlan;
import com.regivolley.api.application.result.MySubscription;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-23: "now" is Monday 12 Oct 2026 in Lisbon. */
@ExtendWith(MockitoExtension.class)
class MyPlanServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private PlanRepository plans;
    @Mock
    private SubscriptionRepository subscriptions;

    private final List<Plan> knownPlans = new ArrayList<>();

    private Association association;
    private Member member;
    private MyPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        member = Data.member(association);
        useCase = new MyPlanService(members, plans, subscriptions, Data.CLOCK);
        lenient().when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
        lenient().when(plans.findByIds(eq(association.id()), any())).thenAnswer(call -> List.copyOf(knownPlans));
    }

    private Plan plan(String name, PlanTerms terms, Integer validityDays) {
        Plan plan = Plan.create(association.id(), name, terms, Money.ofCents(3000), validityDays);
        knownPlans.add(plan);
        return plan;
    }

    private void owns(Subscription... owned) {
        when(subscriptions.findByMember(association.id(), member.id())).thenReturn(List.of(owned));
    }

    @Test
    void showsAPackWithItsRemainingCreditsValidityAndPaymentStatus() {
        // Arrange
        Plan pack = plan("Pack of 10", PlanTerms.pack(10, Set.of()), 90);
        Session session = Data.session(Data.group(association, Data.coach(association), "Beginner"), 5);
        Session booked = Data.booked(session, member);
        Subscription subscription = Data.charged(Subscription.create(pack, member.id(), LocalDate.parse("2026-10-01"), List.of()),
                session, Data.bookingOf(booked, member));
        owns(subscription);

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        assertThat(result.subscriptions()).singleElement().satisfies(entry -> {
            assertThat(entry.subscriptionId()).isEqualTo(subscription.id());
            assertThat(entry.planName()).isEqualTo("Pack of 10");
            assertThat(entry.type()).isEqualTo(PlanType.PACK);
            assertThat(entry.startDate()).isEqualTo(LocalDate.parse("2026-10-01"));
            assertThat(entry.endDate()).isEqualTo(LocalDate.parse("2026-12-29"));
            assertThat(entry.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(entry.remaining()).hasValue(9);
        });
        assertThat(result.asOf()).isEqualTo(LocalDate.parse("2026-10-12"));
    }

    @Test
    void anUnlimitedPlanHasNoRemainingCount() {
        // Arrange
        Plan monthly = plan("Monthly", PlanTerms.monthlyUnlimited(Set.of()), null);
        owns(Subscription.create(monthly, member.id(), LocalDate.parse("2026-10-01"), List.of()).markPaid());

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        MySubscription entry = result.subscriptions().get(0);
        assertThat(entry.remaining()).isEmpty();
        assertThat(entry.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aWeeklyPlanShowsWhatIsLeftThisWeek() {
        // Arrange
        Plan weekly = plan("Twice a week", PlanTerms.monthlyNPerWeek(2, Set.of()), null);
        owns(Subscription.create(weekly, member.id(), LocalDate.parse("2026-10-01"), List.of()));

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        assertThat(result.subscriptions().get(0).remaining()).hasValue(2);
    }

    @Test
    void expiredSubscriptionsAreLeftOutAndFutureOnesShownInOrder() {
        // Arrange
        Plan monthly = plan("Monthly", PlanTerms.monthlyUnlimited(Set.of()), null);
        Subscription expired = Subscription.create(monthly, member.id(), LocalDate.parse("2026-08-01"), List.of());
        Subscription current = Subscription.create(monthly, member.id(), LocalDate.parse("2026-10-01"), List.of());
        Subscription upcoming = Subscription.create(monthly, member.id(), LocalDate.parse("2026-11-01"), List.of());
        owns(expired, current, upcoming);

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        assertThat(result.subscriptions()).extracting(MySubscription::subscriptionId)
                .containsExactly(current.id(), upcoming.id());
    }

    @Test
    void aMemberWithoutSubscriptionsGetsAnEmptyList() {
        // Arrange
        owns();

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        assertThat(result.subscriptions()).isEmpty();
    }

    @Test
    void onlyTheActorsOwnSubscriptionsAreRead() {
        // Arrange
        owns();

        // Act
        useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        verify(subscriptions).findByMember(association.id(), member.id());
    }

    @Test
    void anActorOfAnotherAssociationIsNotFound() {
        // Arrange
        Member foreign = Data.member(Data.association());
        Executable act = () -> useCase.execute(new MyPlanQuery(Data.actor(foreign)));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(subscriptions, org.mockito.Mockito.never()).findByMember(foreign.associationId(), foreign.id());
    }

    @Test
    void thePlansOfAllTheSubscriptionsAreLoadedInOneBatch() {
        // Arrange
        Plan monthly = plan("Monthly", PlanTerms.monthlyUnlimited(Set.of()), null);
        Plan pack = plan("Pack", PlanTerms.pack(10, Set.of()), 90);
        owns(Subscription.create(monthly, member.id(), LocalDate.parse("2026-10-01"), List.of()),
                Subscription.create(pack, member.id(), LocalDate.parse("2026-11-01"), List.of()));

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        assertThat(result.subscriptions()).extracting(MySubscription::planName).containsExactly("Monthly", "Pack");
        verify(plans, times(1)).findByIds(eq(association.id()), any());
        verify(plans, never()).findById(any(), any());
    }

    @Test
    void aSubscriptionWhosePlanCannotBeLoadedIsStillShownWithAPlaceholderName() {
        // Arrange
        Plan vanished = Plan.create(association.id(), "Gone", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        owns(Subscription.create(vanished, member.id(), LocalDate.parse("2026-10-01"), List.of()));

        // Act
        MyPlan result = useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        assertThat(result.subscriptions()).singleElement().satisfies(entry ->
                assertThat(entry.planName()).isEqualTo(MyPlanService.UNKNOWN_PLAN_NAME));
    }

    @Test
    void withNothingToShowNoPlanIsLoadedAtAll() {
        // Arrange
        owns();

        // Act
        useCase.execute(new MyPlanQuery(Data.actor(member)));

        // Assert
        verify(plans, never()).findByIds(any(), any());
    }
}
