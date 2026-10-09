package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
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

    /** The default window around "today" (Lisbon, 12 Oct 2026): one year back, one year ahead, on the day a subscription ends. */
    private static final LocalDate FROM = LocalDate.parse("2025-10-12");
    private static final LocalDate TO = LocalDate.parse("2027-10-12");

    private Association association;
    private Member admin;
    private ListSubscriptionsByPaymentStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        useCase = new ListSubscriptionsByPaymentStatusService(members, subscriptions, Data.CLOCK);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
    }

    @Test
    void listsTheSubscriptionsInThatStatusWithTheMembersName() {
        // Arrange
        Member late = Data.member(association);
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(late));
        Plan plan = PlanFactory.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        Subscription overdue = SubscriptionFactory.create(plan, late.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.OVERDUE, FROM, TO)).thenReturn(List.of(overdue));

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
        Plan plan = PlanFactory.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        Subscription a = SubscriptionFactory.create(plan, first.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        Subscription b = SubscriptionFactory.create(plan, missing.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        Subscription c = SubscriptionFactory.create(plan, second.id(), LocalDate.parse("2026-10-01"), List.of()).markOverdue();
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.OVERDUE, FROM, TO)).thenReturn(List.of(a, b, c));
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
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.PENDING, FROM, TO)).thenReturn(List.of());

        // Act
        List<SubscriptionPaymentEntry> entries = useCase.execute(
                new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.PENDING));

        // Assert
        assertThat(entries).isEmpty();
        verify(members, never()).findByIds(any(), any());
        verify(subscriptions).findByPaymentStatus(association.id(), PaymentStatus.PENDING, FROM, TO);
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
        verify(subscriptions, org.mockito.Mockito.never()).findByPaymentStatus(association.id(), PaymentStatus.OVERDUE, FROM, TO);
    }

    @Test
    void withNoDatesTheWindowIsOneYearEitherSideOfTodayInLisbon() {
        // Arrange
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.OVERDUE, FROM, TO)).thenReturn(List.of());

        // Act
        useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.OVERDUE));

        // Assert
        verify(subscriptions).findByPaymentStatus(association.id(), PaymentStatus.OVERDUE, FROM, TO);
    }

    @Test
    void aSingleBoundGivesTheWindowOfTwoYearsFromOrUpToIt() {
        // Arrange
        LocalDate day = LocalDate.parse("2026-03-01");
        when(subscriptions.findByPaymentStatus(eq(association.id()), eq(PaymentStatus.PAID), any(), any())).thenReturn(List.of());

        // Act
        useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.PAID, day, null));
        useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.PAID, null, day));

        // Assert
        verify(subscriptions).findByPaymentStatus(association.id(), PaymentStatus.PAID, day, day.plusYears(2));
        verify(subscriptions).findByPaymentStatus(association.id(), PaymentStatus.PAID, day.minusYears(2), day);
    }

    @Test
    void anExplicitWindowIsPassedOnAsGiven() {
        // Arrange
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = LocalDate.parse("2027-12-31");
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.PENDING, from, to)).thenReturn(List.of());

        // Act
        useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.PENDING, from, to));

        // Assert
        verify(subscriptions).findByPaymentStatus(association.id(), PaymentStatus.PENDING, from, to);
    }

    @Test
    void aWindowLongerThanTwoYearsOrBackwardsIsRefusedBeforeAnythingIsRead() {
        // Arrange
        LocalDate from = LocalDate.parse("2024-01-01");
        Executable tooLong = () -> useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.OVERDUE,
                from, from.plusYears(2).plusDays(1)));
        Executable backwards = () -> useCase.execute(new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.OVERDUE,
                from.plusDays(1), from));

        // Act
        InvalidFieldException first = assertThrows(InvalidFieldException.class, tooLong);
        InvalidFieldException second = assertThrows(InvalidFieldException.class, backwards);

        // Assert
        assertThat(first.field()).isEqualTo("to");
        assertThat(second.field()).isEqualTo("from");
        verify(subscriptions, never()).findByPaymentStatus(any(), any(), any(), any());
    }

    @Test
    void aWindowOfExactlyTwoYearsIsAccepted() {
        // Arrange
        LocalDate from = LocalDate.parse("2024-01-01");
        when(subscriptions.findByPaymentStatus(association.id(), PaymentStatus.PAID, from, from.plusYears(2))).thenReturn(List.of());

        // Act
        List<SubscriptionPaymentEntry> entries = useCase.execute(
                new ListSubscriptionsByPaymentStatusQuery(Data.actor(admin), PaymentStatus.PAID, from, from.plusYears(2)));

        // Assert
        assertThat(entries).isEmpty();
    }
}
