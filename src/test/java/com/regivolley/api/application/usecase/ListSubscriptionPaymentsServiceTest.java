package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionPaymentsQuery;
import com.regivolley.api.application.result.PaymentLine;
import com.regivolley.api.application.result.SubscriptionPayments;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SubscriptionNotFoundException;
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
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-21, RN-17, RN-19: an administrator reads one subscription's payments and reversals with the totals the ledger derives. */
@ExtendWith(MockitoExtension.class)
class ListSubscriptionPaymentsServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private PaymentRepository payments;

    private Association association;
    private Member admin;
    private Subscription subscription;
    private ListSubscriptionPaymentsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        Plan plan = PlanFactory.create(association.id(), "Pack", PlanTerms.pack(10, Set.of()), Money.ofCents(4500), 90);
        subscription = SubscriptionFactory.create(plan, Data.member(association).id(), LocalDate.parse("2026-10-01"), List.of());
        useCase = new ListSubscriptionPaymentsService(members, subscriptions, payments);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
    }

    private Payment paid(long cents, Member by) {
        return PaymentFactory.create(subscription, Money.ofCents(cents), LocalDate.parse("2026-10-02"), PaymentMethod.CASH, by.id(), Data.CLOCK);
    }

    @Test
    void thePaymentsAndReversalsComeInTheRepositoryOrderWithTotalsFromTheLedger() {
        // Arrange
        Payment first = paid(3000, admin);
        Payment second = paid(1500, admin);
        Payment reversal = PaymentFactory.createReversal(second, admin.id(), Data.CLOCK);
        when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.of(subscription));
        when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of(first, second, reversal));
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(admin));

        // Act
        SubscriptionPayments result = useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(admin), subscription.id()));

        // Assert
        assertThat(result.subscription()).isEqualTo(subscription);
        assertThat(result.payments()).extracting(PaymentLine::payment).containsExactly(first, second, reversal);
        assertThat(result.paid()).isEqualTo(Money.ofCents(3000));
        assertThat(result.outstanding()).isEqualTo(Money.ofCents(1500));
    }

    @Test
    void theRecordersAreNamedInOneBatch() {
        // Arrange
        Member other = Data.admin(association);
        Payment byAdmin = paid(1000, admin);
        Payment byOther = paid(500, other);
        when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.of(subscription));
        when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of(byAdmin, byOther));
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of(admin, other));

        // Act
        SubscriptionPayments result = useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(admin), subscription.id()));

        // Assert
        assertThat(result.payments()).extracting(PaymentLine::recordedByName).containsExactly(Optional.of(admin.name()), Optional.of(other.name()));
        verify(members, times(1)).findByIds(eq(association.id()), any());
    }

    @Test
    void aRecorderWhoCannotBeFoundLeavesTheNameOut() {
        // Arrange
        Member gone = Data.admin(association);
        when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.of(subscription));
        when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of(paid(1000, gone)));
        when(members.findByIds(eq(association.id()), any())).thenReturn(List.of());

        // Act
        SubscriptionPayments result = useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(admin), subscription.id()));

        // Assert
        assertThat(result.payments()).singleElement().satisfies(line -> assertThat(line.recordedByName()).isEmpty());
    }

    @Test
    void aSubscriptionWithoutPaymentsOwesItsWholePriceAndReadsNoMembers() {
        // Arrange
        when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.of(subscription));
        when(payments.findBySubscription(association.id(), subscription.id())).thenReturn(List.of());

        // Act
        SubscriptionPayments result = useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(admin), subscription.id()));

        // Assert
        assertThat(result.payments()).isEmpty();
        assertThat(result.paid()).isEqualTo(Money.ofCents(0));
        assertThat(result.outstanding()).isEqualTo(Money.ofCents(4500));
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aSubscriptionOfAnotherAssociationOrNoneAtAllIsNotFoundAndNoPaymentIsRead() {
        // Arrange
        when(subscriptions.findById(association.id(), subscription.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(admin), subscription.id()));

        // Act
        assertThrows(SubscriptionNotFoundException.class, act);

        // Assert
        verifyNoInteractions(payments);
    }

    @Test
    void aNonAdministratorIsNotAllowedAndNothingIsRead() {
        // Arrange
        Member plain = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(plain), subscription.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(subscriptions, payments);
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListSubscriptionPaymentsQuery(Data.actor(stranger), subscription.id()));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(subscriptions, payments);
    }
}
