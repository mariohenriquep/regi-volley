package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetMemberDetailQuery;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.result.LevelChangeEntry;
import com.regivolley.api.application.result.MemberDetail;
import com.regivolley.api.application.result.MemberSubscriptionEntry;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-04, US-07, US-20, US-21: one member as the administrator sees them, with names resolved in batches and amounts from the ledger. */
@ExtendWith(MockitoExtension.class)
class GetMemberDetailServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private MembershipStore memberships;
    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private PlanRepository plans;
    @Mock
    private PaymentRepository payments;

    private Association association;
    private Member admin;
    private GetMemberDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        useCase = new GetMemberDetailService(members, associations, memberships, subscriptions, plans, payments);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(subscriptions.findByMember(any(), any())).thenReturn(List.of());
    }

    private MemberDetail detailOf(Member target) {
        return useCase.execute(new GetMemberDetailQuery(Data.actor(admin), target.id()));
    }

    private Membership membership(Member member, boolean confirmed) {
        return confirmed
                ? new Membership(UUID.randomUUID(), UUID.randomUUID(), association.id(), member.id(),
                com.regivolley.api.application.identity.MembershipStatus.CONFIRMED, Data.NOW, Data.NOW)
                : Membership.pending(UUID.randomUUID(), association.id(), member.id(), Data.NOW);
    }

    private Plan plan(String name) {
        return PlanFactory.create(association.id(), name, PlanTerms.pack(10, Set.of()), Money.ofCents(4500), 90);
    }

    @Test
    void theMemberComesBackWithTheLevelNameAndTheActivationState() {
        // Arrange
        Member target = Data.atLevel(Data.member(association), association, "Intermediate");
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(association.id(), target.id())).thenReturn(Optional.of(membership(target, true)));

        // Act
        MemberDetail detail = detailOf(target);

        // Assert
        assertThat(detail.member()).isEqualTo(target);
        assertThat(detail.levelName()).contains("Intermediate");
        assertThat(detail.accountActivated()).isTrue();
        assertThat(detail.levelChanges()).hasSize(1);
        assertThat(detail.subscriptions()).isEmpty();
        assertThat(detail.subscriptionsTruncated()).isFalse();
    }

    @Test
    void aPendingMembershipOrNoMembershipMeansTheAccountIsNotActivated() {
        // Arrange
        Member pending = Data.member(association);
        Member without = Data.member(association);
        when(members.findById(association.id(), pending.id())).thenReturn(Optional.of(pending));
        when(members.findById(association.id(), without.id())).thenReturn(Optional.of(without));
        when(memberships.findByMember(association.id(), pending.id())).thenReturn(Optional.of(membership(pending, false)));
        when(memberships.findByMember(association.id(), without.id())).thenReturn(Optional.empty());

        // Act
        MemberDetail pendingDetail = detailOf(pending);
        MemberDetail withoutDetail = detailOf(without);

        // Assert
        assertThat(pendingDetail.accountActivated()).isFalse();
        assertThat(withoutDetail.accountActivated()).isFalse();
    }

    @Test
    void theLevelHistoryNamesTheLevelsAndWhoChangedThemInOneBatch() {
        // Arrange
        Member coach = Data.coach(association);
        Member target = Data.member(association)
                .changeLevel(association, Data.level(association, "Intermediate"), coach.id(), Data.CLOCK)
                .changeLevel(association, Data.level(association, "Advanced"), admin.id(), Data.CLOCK);
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(coach, admin));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());

        // Act
        MemberDetail detail = detailOf(target);

        // Assert
        assertThat(detail.levelChanges()).extracting(LevelChangeEntry::fromName).containsExactly(Optional.of("Beginner"), Optional.of("Intermediate"));
        assertThat(detail.levelChanges()).extracting(LevelChangeEntry::toName).containsExactly(Optional.of("Intermediate"), Optional.of("Advanced"));
        assertThat(detail.levelChanges()).extracting(LevelChangeEntry::changedByName).containsExactly(Optional.of(coach.name()), Optional.of(admin.name()));
        assertThat(detail.levelChanges()).extracting(entry -> entry.change().changedBy()).containsExactly(coach.id(), admin.id());
        verify(members, times(1)).findByIds(eq(association.id()), any());
    }

    @Test
    void aChangerWhoCannotBeFoundLeavesTheirNameOutOfTheEntry() {
        // Arrange
        Member gone = Data.coach(association);
        Member target = Data.member(association).changeLevel(association, Data.level(association, "Advanced"), gone.id(), Data.CLOCK);
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of());
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());

        // Act
        MemberDetail detail = detailOf(target);

        // Assert
        assertThat(detail.levelChanges()).singleElement().satisfies(entry -> assertThat(entry.changedByName()).isEmpty());
    }

    @Test
    void aMemberWithoutLevelChangesReadsNoOtherMembers() {
        // Arrange
        Member target = Data.member(association);
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());

        // Act
        MemberDetail detail = detailOf(target);

        // Assert
        assertThat(detail.levelChanges()).isEmpty();
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void subscriptionsComeNewestFirstWithThePlanNameAndAmountsFromTheLedger() {
        // Arrange
        Member target = Data.member(association);
        Plan packPlan = plan("Ten sessions");
        Subscription older = SubscriptionFactory.create(packPlan, target.id(), LocalDate.parse("2026-01-05"), List.of());
        Subscription newer = SubscriptionFactory.create(packPlan, target.id(), LocalDate.parse("2026-10-01"), List.of());
        Payment part = PaymentFactory.create(newer, Money.ofCents(1500), LocalDate.parse("2026-10-02"), PaymentMethod.CASH, admin.id(), Data.CLOCK);
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(older, newer));
        when(plans.findByIds(eq(association.id()), any())).thenReturn(List.of(packPlan));
        when(payments.findBySubscriptions(eq(association.id()), any())).thenReturn(List.of(part));

        // Act
        MemberDetail detail = detailOf(target);

        // Assert
        assertThat(detail.subscriptions()).extracting(MemberSubscriptionEntry::subscription).containsExactly(newer, older);
        assertThat(detail.subscriptions()).extracting(MemberSubscriptionEntry::planName).containsExactly(Optional.of("Ten sessions"), Optional.of("Ten sessions"));
        assertThat(detail.subscriptions()).extracting(entry -> entry.paid().cents()).containsExactly(1500L, 0L);
        assertThat(detail.subscriptions()).extracting(entry -> entry.outstanding().cents()).containsExactly(3000L, 4500L);
        assertThat(detail.subscriptions().get(0).subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void plansAndPaymentsAreLoadedOnceForAllTheSubscriptions() {
        // Arrange
        Member target = Data.member(association);
        Plan first = plan("A");
        Plan second = plan("B");
        Subscription a = SubscriptionFactory.create(first, target.id(), LocalDate.parse("2026-01-05"), List.of());
        Subscription b = SubscriptionFactory.create(second, target.id(), LocalDate.parse("2026-10-01"), List.of());
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(a, b));
        when(plans.findByIds(eq(association.id()), any())).thenReturn(List.of(first, second));
        when(payments.findBySubscriptions(eq(association.id()), any())).thenReturn(List.of());

        // Act
        detailOf(target);

        // Assert
        verify(plans, times(1)).findByIds(eq(association.id()), any(Collection.class));
        verify(payments, times(1)).findBySubscriptions(eq(association.id()), any(Collection.class));
        verify(payments, never()).findBySubscription(any(), any());
    }

    @Test
    void aPlanThatCannotBeFoundLeavesItsNameOutButKeepsTheSubscription() {
        // Arrange
        Member target = Data.member(association);
        Subscription orphan = SubscriptionFactory.create(plan("Gone"), target.id(), LocalDate.parse("2026-10-01"), List.of());
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(orphan));
        when(plans.findByIds(eq(association.id()), any())).thenReturn(List.of());
        when(payments.findBySubscriptions(eq(association.id()), any())).thenReturn(List.of());

        // Act
        MemberDetail detail = detailOf(target);

        // Assert
        assertThat(detail.subscriptions()).singleElement().satisfies(entry -> {
            assertThat(entry.planName()).isEmpty();
            assertThat(entry.subscription()).isEqualTo(orphan);
        });
    }

    @Test
    void onlyTheMostRecentSubscriptionsAreShownAndTheCutIsFlagged() {
        // Arrange
        Member target = Data.member(association);
        Plan single = PlanFactory.create(association.id(), "One", PlanTerms.singleSession(Set.of()), Money.ofCents(500), 1);
        List<Subscription> all = IntStream.range(0, GetMemberDetailService.MAX_SUBSCRIPTIONS + 1)
                .mapToObj(day -> SubscriptionFactory.create(single, target.id(), LocalDate.parse("2020-01-01").plusDays(day), List.of())).toList();
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(all);
        when(plans.findByIds(eq(association.id()), any())).thenReturn(List.of(single));
        when(payments.findBySubscriptions(eq(association.id()), any())).thenReturn(List.of());

        // Act
        MemberDetail detail = detailOf(target);

        // Assert - the oldest one (day 0) is the one left out
        assertThat(detail.subscriptionsTruncated()).isTrue();
        assertThat(detail.subscriptions()).hasSize(GetMemberDetailService.MAX_SUBSCRIPTIONS);
        assertThat(detail.subscriptions()).extracting(MemberSubscriptionEntry::subscription).doesNotContain(all.get(0));
        assertThat(detail.subscriptions().get(0).subscription()).isEqualTo(all.get(all.size() - 1));
    }

    @Test
    void noSubscriptionsMeansNoPlanOrPaymentQueries() {
        // Arrange
        Member target = Data.member(association);
        when(members.findById(association.id(), target.id())).thenReturn(Optional.of(target));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());

        // Act
        detailOf(target);

        // Assert
        verifyNoInteractions(plans, payments);
    }

    @Test
    void anAnonymisedMemberIsStillShownWithItsPlaceholders() {
        // Arrange
        Member erased = Data.member(association).anonymise(Data.CLOCK);
        when(members.findById(association.id(), erased.id())).thenReturn(Optional.of(erased));
        when(memberships.findByMember(any(), any())).thenReturn(Optional.empty());

        // Act
        MemberDetail detail = detailOf(erased);

        // Assert
        assertThat(detail.member().isAnonymised()).isTrue();
        assertThat(detail.accountActivated()).isFalse();
    }

    @Test
    void aMemberOfAnotherAssociationOrNoneAtAllIsNotFoundAndNothingElseIsRead() {
        // Arrange
        Member foreign = Data.member(Data.association());
        when(members.findById(association.id(), foreign.id())).thenReturn(Optional.empty());
        Executable act = () -> detailOf(foreign);

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(memberships, subscriptions, plans, payments);
    }

    @Test
    void aNonAdministratorIsNotAllowedAndTheTargetIsNotEvenLoaded() {
        // Arrange
        Member plain = Data.member(association);
        Member target = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> useCase.execute(new GetMemberDetailQuery(Data.actor(plain), target.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).findById(association.id(), target.id());
        verifyNoInteractions(memberships, subscriptions, plans, payments);
    }
}
