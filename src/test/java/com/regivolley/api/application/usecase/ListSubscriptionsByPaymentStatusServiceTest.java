package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.repository.MemberRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-22: who owes what, filtered by payment status (CSV export belongs to the web layer). */
@ExtendWith(MockitoExtension.class)
class ListSubscriptionsByPaymentStatusServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private SubscriptionRepository subscriptions;

    private Association association;
    private Member admin;
    private ListSubscriptionsByPaymentStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        useCase = new ListSubscriptionsByPaymentStatusService(members, subscriptions);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
    }

    @Test
    void listsTheSubscriptionsInThatStatusWithTheMembersName() {
        // Arrange
        Member late = Data.member(association);
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(late));
        Plan plan = Plan.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        Subscription overdue = Subscription.create(plan, late.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.OVERDUE)).thenReturn(List.of(overdue));

        // Act
        List<SubscriptionPaymentEntry> entries = useCase.execute(
                new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.OVERDUE));

        // Assert
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.subscription()).isEqualTo(overdue);
            assertThat(entry.memberName()).isEqualTo(late.name());
        });
    }

    @Test
    void membersAreLoadedInOneBatchAndOneMissingMemberDoesNotFailTheWholeList() {
        // Arrange
        Member first = Data.member(association);
        Member second = Data.member(association);
        Member missing = Data.member(association);
        Plan plan = Plan.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        Subscription a = Subscription.create(plan, first.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        Subscription b = Subscription.create(plan, missing.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        Subscription c = Subscription.create(plan, second.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.OVERDUE)).thenReturn(List.of(a, b, c));
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(first, second));

        // Act
        List<SubscriptionPaymentEntry> entries = useCase.execute(
                new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.OVERDUE));

        // Assert
        assertThat(entries).extracting(SubscriptionPaymentEntry::subscription).containsExactly(a, c);
        verify(members, times(1)).findByIds(eq(association.id()), any());
        verify(members, never()).findById(association.id(), first.id());
    }

    @Test
    void anEmptyStatusGivesAnEmptyListAndOnlyTheActorsAssociationIsQueried() {
        // Arrange
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.PENDING)).thenReturn(List.of());

        // Act
        List<SubscriptionPaymentEntry> entries = useCase.execute(
                new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.PENDING));

        // Assert
        assertThat(entries).isEmpty();
        verify(members, never()).findByIds(any(), any());
        verify(subscriptions).findByPaymentStatus(association.id(), PaymentStatus.PENDING);
    }

    @Test
    void aNonAdministratorIsNotAllowed() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(coach), PaymentStatus.OVERDUE));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(subscriptions, org.mockito.Mockito.never()).findByPaymentStatus(association.id(), PaymentStatus.OVERDUE);
    }
}
