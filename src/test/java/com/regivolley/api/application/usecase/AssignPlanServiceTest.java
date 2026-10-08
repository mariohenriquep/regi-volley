package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.domain.exception.MemberInactiveException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.PlanNotFoundException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SubscriptionOverlapException;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-20, RN-16: "now" is Monday 12 Oct 2026 in Lisbon. */
@ExtendWith(MockitoExtension.class)
class AssignPlanServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-12");

    @Mock
    private MemberRepository members;
    @Mock
    private PlanRepository plans;
    @Mock
    private SubscriptionRepository subscriptions;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member target;
    private Plan monthly;
    private AssignPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        target = Data.member(association);
        monthly = PlanFactory.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        useCase = new AssignPlanService(members, plans, subscriptions, transactions, Data.CLOCK);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(members.findByIdForUpdate(association.id(), target.id())).thenReturn(Optional.of(target));
        lenient().when(plans.findById(association.id(), monthly.id())).thenReturn(Optional.of(monthly));
        lenient().when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of());
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
    }

    private AssignPlanCommand assign(PlanId plan, LocalDate start) {
        return new AssignPlanCommand(Data.actor(admin), target.id(), plan, start);
    }

    @Test
    void assignsThePlanFromTheGivenDateAsPendingWithAPlanSnapshot() {
        // Arrange
        AssignPlanCommand command = assign(monthly.id(), LocalDate.parse("2026-10-20"));

        // Act
        Subscription subscription = useCase.execute(command);

        // Assert
        assertThat(subscription.memberId()).isEqualTo(target.id());
        assertThat(subscription.associationId()).isEqualTo(association.id());
        assertThat(subscription.planId()).isEqualTo(monthly.id());
        assertThat(subscription.startDate()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(subscription.endDate()).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(subscription.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(subscriptions).save(subscription);
        verify(members).findByIdForUpdate(association.id(), target.id());
    }

    @Test
    void withoutADateAMemberWithNoSubscriptionStartsToday() {
        // Arrange
        AssignPlanCommand command = assign(monthly.id(), null);

        // Act
        Subscription subscription = useCase.execute(command);

        // Assert
        assertThat(subscription.startDate()).isEqualTo(TODAY);
    }

    @Test
    void withoutADateItRenewsTheLatestSubscriptionTheDayAfterItEnds() {
        // Arrange
        Subscription current = SubscriptionFactory.create(monthly, target.id(), LocalDate.parse("2026-10-01"), List.of());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(current));

        // Act
        Subscription renewal = useCase.execute(assign(monthly.id(), null));

        // Assert
        assertThat(renewal.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
    }

    @Test
    void withoutADateAnExhaustedPackIsRenewedAtOnce() {
        // Arrange
        Plan pack = PlanFactory.create(association.id(), "Single", PlanTerms.singleSession(Set.of()), Money.ofCents(700), 30);
        when(plans.findById(association.id(), pack.id())).thenReturn(Optional.of(pack));
        Subscription used = Data.charged(SubscriptionFactory.create(pack, target.id(), LocalDate.parse("2026-10-01"), List.of()),
                Data.session(Data.group(association, Data.coach(association), "Beginner"), 5),
                Data.bookingOf(Data.booked(Data.session(Data.group(association, Data.coach(association), "Beginner"), 5), target), target));
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(used));

        // Act
        Subscription renewal = useCase.execute(assign(pack.id(), null));

        // Assert
        assertThat(renewal.startDate()).isEqualTo(TODAY);
    }

    @Test
    void anOverlappingPeriodIsRefusedAndNothingIsStored() {
        // Arrange
        Subscription current = SubscriptionFactory.create(monthly, target.id(), LocalDate.parse("2026-10-01"), List.of());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(current));
        Executable act = () -> useCase.execute(assign(monthly.id(), LocalDate.parse("2026-10-15")));

        // Act
        assertThrows(SubscriptionOverlapException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void aFreePlanNeedsNoPaymentSoItStartsPaid() {
        // Arrange
        Plan free = PlanFactory.create(association.id(), "Trial", PlanTerms.singleSession(Set.of()), Money.ofCents(0), 7);
        when(plans.findById(association.id(), free.id())).thenReturn(Optional.of(free));

        // Act
        Subscription subscription = useCase.execute(assign(free.id(), null));

        // Assert
        assertThat(subscription.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aPlanOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> useCase.execute(assign(PlanId.generate(), null));

        // Act
        assertThrows(PlanNotFoundException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void aMemberOfAnotherAssociationIsNotFound() {
        // Arrange
        Member foreign = Data.member(Data.association());
        Executable act = () -> useCase.execute(new AssignPlanCommand(Data.actor(admin), foreign.id(), monthly.id(), null));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new AssignPlanCommand(Data.actor(coach), target.id(), monthly.id(), null));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(subscriptions, never()).save(any(Subscription.class));
        verify(members, never()).findByIdForUpdate(any(), any());
    }

    @Test
    void anInactiveMemberCannotBeGivenAPlan() {
        // Arrange
        Member gone = target.deactivate();
        when(members.findByIdForUpdate(association.id(), target.id())).thenReturn(Optional.of(gone));
        Executable act = () -> useCase.execute(assign(monthly.id(), null));

        // Act
        MemberInactiveException ex = assertThrows(MemberInactiveException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(target.id());
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void retriesInANewTransactionOnAConflictAndRechecksTheOverlap() {
        // Arrange
        when(subscriptions.save(any(Subscription.class)))
                .thenThrow(new SubscriptionModifiedConcurrentlyException(com.regivolley.api.domain.model.valueobject.SubscriptionId.generate()))
                .thenAnswer(returnsFirstArg());

        // Act
        Subscription subscription = useCase.execute(assign(monthly.id(), null));

        // Assert
        assertThat(subscription).isNotNull();
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
