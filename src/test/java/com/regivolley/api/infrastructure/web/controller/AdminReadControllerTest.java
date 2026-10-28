package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.GetMemberDetailQuery;
import com.regivolley.api.application.command.ListMembersQuery;
import com.regivolley.api.application.command.ListSubscriptionPaymentsQuery;
import com.regivolley.api.application.command.ListTrainingGroupsQuery;
import com.regivolley.api.application.result.LevelChangeEntry;
import com.regivolley.api.application.result.MemberDetail;
import com.regivolley.api.application.result.MemberListEntry;
import com.regivolley.api.application.result.MemberListing;
import com.regivolley.api.application.result.MemberSubscriptionEntry;
import com.regivolley.api.application.result.PaymentLine;
import com.regivolley.api.application.result.PlanListEntry;
import com.regivolley.api.application.result.SubscriptionPayments;
import com.regivolley.api.application.result.TrainingGroupListEntry;
import com.regivolley.api.application.result.VenueListEntry;
import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The administrator's read endpoints (issue #49) over HTTP: the wire shape of each answer and the query the controller builds. */
class AdminReadControllerTest extends AbstractControllerWebTest {

    private Association other;
    private Member ana;

    @BeforeEach
    void setUpFixtures() {
        other = WebFixtures.association();
        ana = WebFixtures.member(other, MemberRole.ADMIN, MemberRole.COACH, MemberRole.MEMBER);
    }

    @Test
    void theMemberListShowsTheMinimalColumnsAndTheCaptureHasTheFiltersAndTheCallersActor() throws Exception {
        // Arrange
        when(listMembersUseCase.execute(any())).thenReturn(new MemberListing(
                List.of(new MemberListEntry(ana, Optional.of("Beginner"), true)), true));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/members?status=INACTIVE&q=ana");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.members[0].memberId").value(ana.id().value().toString()))
                .andExpect(jsonPath("$.members[0].name").value("Ana Silva"))
                .andExpect(jsonPath("$.members[0].email").value("ana.silva@example.com"))
                .andExpect(jsonPath("$.members[0].phone").value("912345678"))
                .andExpect(jsonPath("$.members[0].levelId").value(ana.levelId().value().toString()))
                .andExpect(jsonPath("$.members[0].levelName").value("Beginner"))
                .andExpect(jsonPath("$.members[0].roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.members[0].roles[1]").value("COACH"))
                .andExpect(jsonPath("$.members[0].roles[2]").value("ADMIN"))
                .andExpect(jsonPath("$.members[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.members[0].accountActivated").value(true))
                .andExpect(jsonPath("$.members[0].joinedAt").value("2026-10-12T09:00:00Z"));
        ArgumentCaptor<ListMembersQuery> query = ArgumentCaptor.forClass(ListMembersQuery.class);
        verify(listMembersUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
        assertThat(query.getValue().status()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(query.getValue().text()).isEqualTo("ana");
    }

    @Test
    void withoutFiltersTheQueryHasNone() throws Exception {
        // Arrange
        when(listMembersUseCase.execute(any())).thenReturn(new MemberListing(List.of(), false));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/members");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.members").isEmpty()).andExpect(jsonPath("$.truncated").value(false));
        ArgumentCaptor<ListMembersQuery> query = ArgumentCaptor.forClass(ListMembersQuery.class);
        verify(listMembersUseCase).execute(query.capture());
        assertThat(query.getValue().status()).isNull();
        assertThat(query.getValue().text()).isNull();
    }

    @Test
    void anUnknownStatusIs400() throws Exception {
        // Arrange
        String path = "/api/v1/members?status=DELETED";

        // Act
        var result = authenticated(HttpMethod.GET, path);

        // Assert
        result.andExpect(status().isBadRequest());
    }

    @Test
    void theMemberListNeverPrintsPersonalDataInToString() {
        // Arrange
        var response = com.regivolley.api.infrastructure.web.mapper.MemberWebMapper.toResponse(
                new MemberListing(List.of(new MemberListEntry(ana, Optional.empty(), false)), false));

        // Act
        String text = response.toString() + response.members().get(0);

        // Assert
        assertThat(text).doesNotContain("Ana").doesNotContain("example.com").doesNotContain("912345678");
    }

    @Test
    void theMemberDetailHasTheHistoryAndTheSubscriptionsWithAmountsAndCredits() throws Exception {
        // Arrange
        Member changer = WebFixtures.member(other, MemberRole.ADMIN);
        Member moved = ana.changeLevel(other, other.levels().get(1).id(), changer.id(), WebFixtures.CLOCK);
        Plan pack = WebFixtures.pack(other);
        Subscription subscription = WebFixtures.subscription(pack, moved.id());
        Plan monthly = WebFixtures.monthly(other);
        Subscription unlimited = WebFixtures.subscription(monthly, moved.id());
        when(getMemberDetailUseCase.execute(any())).thenReturn(new MemberDetail(moved, Optional.of("Intermediate"), false,
                List.of(new LevelChangeEntry(moved.levelChanges().get(0), Optional.of("Beginner"), Optional.of("Intermediate"),
                        Optional.of(changer.name()))),
                List.of(new MemberSubscriptionEntry(subscription, Optional.of("Ten sessions"), Money.ofCents(1500), Money.ofCents(3000)),
                        new MemberSubscriptionEntry(unlimited, Optional.empty(), Money.ofCents(0), Money.ofCents(3000))),
                true));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/members/" + moved.id().value());

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(moved.id().value().toString()))
                .andExpect(jsonPath("$.anonymised").value(false))
                .andExpect(jsonPath("$.email").value("ana.silva@example.com"))
                .andExpect(jsonPath("$.levelName").value("Intermediate"))
                .andExpect(jsonPath("$.accountActivated").value(false))
                .andExpect(jsonPath("$.levelChanges[0].fromLevelName").value("Beginner"))
                .andExpect(jsonPath("$.levelChanges[0].toLevelName").value("Intermediate"))
                .andExpect(jsonPath("$.levelChanges[0].changedBy").value(changer.id().value().toString()))
                .andExpect(jsonPath("$.levelChanges[0].changedByName").value("Ana Silva"))
                .andExpect(jsonPath("$.levelChanges[0].changedAt").value("2026-10-12T09:00:00Z"))
                .andExpect(jsonPath("$.subscriptionsTruncated").value(true))
                .andExpect(jsonPath("$.subscriptions[0].planName").value("Ten sessions"))
                .andExpect(jsonPath("$.subscriptions[0].type").value("PACK"))
                .andExpect(jsonPath("$.subscriptions[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.subscriptions[0].priceCents").value(4500))
                .andExpect(jsonPath("$.subscriptions[0].paidCents").value(1500))
                .andExpect(jsonPath("$.subscriptions[0].outstandingCents").value(3000))
                .andExpect(jsonPath("$.subscriptions[0].creditsUsed").value(0))
                .andExpect(jsonPath("$.subscriptions[0].creditsLeft").value(10))
                .andExpect(jsonPath("$.subscriptions[1].planName").doesNotExist())
                .andExpect(jsonPath("$.subscriptions[1].creditsLeft").doesNotExist());
        ArgumentCaptor<GetMemberDetailQuery> query = ArgumentCaptor.forClass(GetMemberDetailQuery.class);
        verify(getMemberDetailUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
        assertThat(query.getValue().memberId().value()).isEqualTo(moved.id().value());
    }

    @Test
    void anAnonymisedMemberIsShownWithoutEmailOrPhone() throws Exception {
        // Arrange
        Member erased = ana.anonymise(WebFixtures.CLOCK);
        when(getMemberDetailUseCase.execute(any())).thenReturn(new MemberDetail(erased, Optional.of("Beginner"), false, List.of(), List.of(), false));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/members/" + erased.id().value());

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymised").value(true))
                .andExpect(jsonPath("$.name").value("Anonymised member"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"));
    }

    @Test
    void aMalformedMemberIdIs400() throws Exception {
        // Arrange
        String path = "/api/v1/members/not-a-uuid";

        // Act
        var result = authenticated(HttpMethod.GET, path);

        // Assert
        result.andExpect(status().isBadRequest());
    }

    @Test
    void theLevelsAreTheOnesEveryLevelOperationAnswers() throws Exception {
        // Arrange
        when(listLevelsUseCase.execute(any())).thenReturn(other);

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/levels");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.levels[0].name").value("Beginner"))
                .andExpect(jsonPath("$.levels[1].rank").value(1))
                .andExpect(jsonPath("$.entryLevelId").value(other.entryLevelId().value().toString()));
    }

    @Test
    void theVenuesCarryTheInUseFlag() throws Exception {
        // Arrange
        Venue busy = WebFixtures.venue(other);
        when(listVenuesUseCase.execute(any())).thenReturn(List.of(new VenueListEntry(busy, true)));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/venues");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(busy.id().value().toString()))
                .andExpect(jsonPath("$[0].name").value("Pavilion One"))
                .andExpect(jsonPath("$[0].address").value("Rua A 1, Lisbon"))
                .andExpect(jsonPath("$[0].courts").value(2))
                .andExpect(jsonPath("$[0].inUseByActiveGroup").value(true));
    }

    @Test
    void theGroupsNameTheirLevelsVenueAndCoachAndArchivedOnesAreOptIn() throws Exception {
        // Arrange
        Venue venue = WebFixtures.venue(other);
        TrainingGroup group = WebFixtures.group(other, venue, ana.id());
        when(listTrainingGroupsUseCase.execute(any())).thenReturn(List.of(
                new TrainingGroupListEntry(group, List.of(other.levels().get(0)), Optional.of("Pavilion One"), Optional.empty())));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/training-groups?includeArchived=true");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(group.id().value().toString()))
                .andExpect(jsonPath("$[0].name").value("Wednesday Beginners"))
                .andExpect(jsonPath("$[0].acceptedLevels[0].id").value(other.entryLevelId().value().toString()))
                .andExpect(jsonPath("$[0].acceptedLevels[0].name").value("Beginner"))
                .andExpect(jsonPath("$[0].venue.id").value(venue.id().value().toString()))
                .andExpect(jsonPath("$[0].venue.name").value("Pavilion One"))
                .andExpect(jsonPath("$[0].schedule[0].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$[0].schedule[0].startTime").value("20:00"))
                .andExpect(jsonPath("$[0].schedule[0].durationMinutes").value(90))
                .andExpect(jsonPath("$[0].capacity").value(12))
                .andExpect(jsonPath("$[0].coach.id").value(ana.id().value().toString()))
                .andExpect(jsonPath("$[0].coach.name").doesNotExist())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        ArgumentCaptor<ListTrainingGroupsQuery> query = ArgumentCaptor.forClass(ListTrainingGroupsQuery.class);
        verify(listTrainingGroupsUseCase).execute(query.capture());
        assertThat(query.getValue().includeArchived()).isTrue();
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void archivedGroupsAreLeftOutByDefault() throws Exception {
        // Arrange
        when(listTrainingGroupsUseCase.execute(any())).thenReturn(List.of());

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/training-groups");

        // Assert
        result.andExpect(status().isOk()).andExpect(content().json("[]"));
        ArgumentCaptor<ListTrainingGroupsQuery> query = ArgumentCaptor.forClass(ListTrainingGroupsQuery.class);
        verify(listTrainingGroupsUseCase).execute(query.capture());
        assertThat(query.getValue().includeArchived()).isFalse();
    }

    @Test
    void thePlansShowTheTermsOfTheirTypeThePriceAndTheAllowedLevelsNamed() throws Exception {
        // Arrange
        Plan pack = WebFixtures.pack(other);
        Plan monthly = WebFixtures.monthly(other);
        when(listPlansUseCase.execute(any())).thenReturn(List.of(
                new PlanListEntry(pack, List.of(other.levels().get(0))), new PlanListEntry(monthly, List.of())));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/plans");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ten sessions"))
                .andExpect(jsonPath("$[0].type").value("PACK"))
                .andExpect(jsonPath("$[0].credits").value(10))
                .andExpect(jsonPath("$[0].sessionsPerWeek").doesNotExist())
                .andExpect(jsonPath("$[0].priceCents").value(4500))
                .andExpect(jsonPath("$[0].validityDays").value(90))
                .andExpect(jsonPath("$[0].allowedLevels[0].name").value("Beginner"))
                .andExpect(jsonPath("$[1].type").value("MONTHLY_UNLIMITED"))
                .andExpect(jsonPath("$[1].validityDays").doesNotExist())
                .andExpect(jsonPath("$[1].allowedLevels").isEmpty());
    }

    @Test
    void theSubscriptionsPaymentsAndReversalsComeWithTheTotals() throws Exception {
        // Arrange
        Subscription subscription = WebFixtures.subscription(WebFixtures.pack(other), ana.id());
        Payment payment = WebFixtures.payment(subscription, ana.id());
        Payment reversal = PaymentFactory.createReversal(payment, ana.id(), WebFixtures.CLOCK);
        when(listSubscriptionPaymentsUseCase.execute(any())).thenReturn(new SubscriptionPayments(subscription,
                List.of(new PaymentLine(payment, Optional.of("Ana Silva")), new PaymentLine(reversal, Optional.empty())),
                Money.ofCents(0), Money.ofCents(4500)));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/subscriptions/" + subscription.id().value() + "/payments");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value(subscription.id().value().toString()))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.priceCents").value(4500))
                .andExpect(jsonPath("$.paidCents").value(0))
                .andExpect(jsonPath("$.outstandingCents").value(4500))
                .andExpect(jsonPath("$.payments[0].id").value(payment.id().value().toString()))
                .andExpect(jsonPath("$.payments[0].amountCents").value(1500))
                .andExpect(jsonPath("$.payments[0].paidOn").value("2026-10-12"))
                .andExpect(jsonPath("$.payments[0].method").value("CASH"))
                .andExpect(jsonPath("$.payments[0].recordedBy").value(ana.id().value().toString()))
                .andExpect(jsonPath("$.payments[0].recordedByName").value("Ana Silva"))
                .andExpect(jsonPath("$.payments[0].reversalOf").doesNotExist())
                .andExpect(jsonPath("$.payments[1].reversalOf").value(payment.id().value().toString()))
                .andExpect(jsonPath("$.payments[1].recordedByName").doesNotExist());
        ArgumentCaptor<ListSubscriptionPaymentsQuery> query = ArgumentCaptor.forClass(ListSubscriptionPaymentsQuery.class);
        verify(listSubscriptionPaymentsUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
        assertThat(query.getValue().subscriptionId().value()).isEqualTo(subscription.id().value());
    }

    @Test
    void aSubscriptionWithoutPaymentsAnswersAnEmptyList() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(listSubscriptionPaymentsUseCase.execute(any())).thenReturn(new SubscriptionPayments(
                WebFixtures.subscription(WebFixtures.monthly(other), ana.id()), List.of(), Money.ofCents(0), Money.ofCents(3000)));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/subscriptions/" + id + "/payments");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty());
    }
}
