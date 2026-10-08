package com.regivolley.api.application;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.command.ArchiveTrainingGroupCommand;
import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.command.DeleteVenueCommand;
import com.regivolley.api.application.command.EditPlanCommand;
import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.application.command.MarkSubscriptionOverdueCommand;
import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.command.MyPlanQuery;
import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.application.command.ReversePaymentCommand;
import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.result.AssociationRegistered;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.application.result.MyPlan;
import com.regivolley.api.application.result.PaymentRecorded;
import com.regivolley.api.application.result.PaymentReversed;
import com.regivolley.api.application.result.PlacedBooking;
import com.regivolley.api.application.usecase.ApproveJoinRequestUseCase;
import com.regivolley.api.application.usecase.ArchiveTrainingGroupUseCase;
import com.regivolley.api.application.usecase.AssignPlanUseCase;
import com.regivolley.api.application.usecase.BookSessionUseCase;
import com.regivolley.api.application.usecase.CreatePlanUseCase;
import com.regivolley.api.application.usecase.CreateTrainingGroupUseCase;
import com.regivolley.api.application.usecase.CreateVenueUseCase;
import com.regivolley.api.application.usecase.DeactivateMemberUseCase;
import com.regivolley.api.application.usecase.DeleteVenueUseCase;
import com.regivolley.api.application.usecase.EditPlanUseCase;
import com.regivolley.api.application.usecase.GrantRoleUseCase;
import com.regivolley.api.application.usecase.MarkSubscriptionOverdueUseCase;
import com.regivolley.api.application.usecase.ListSubscriptionsByPaymentStatusUseCase;
import com.regivolley.api.application.usecase.MyPlanUseCase;
import com.regivolley.api.application.usecase.RecordPaymentUseCase;
import com.regivolley.api.application.usecase.RegisterAssociationUseCase;
import com.regivolley.api.application.usecase.RevokeRoleUseCase;
import com.regivolley.api.application.usecase.ReversePaymentUseCase;
import com.regivolley.api.application.usecase.SubmitJoinRequestUseCase;
import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.PaymentExceedsOutstandingException;
import com.regivolley.api.domain.exception.PaymentNotReversibleException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.exception.VenueInUseException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.result.JoinRequestApproval;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

/**
 * The admin use cases wired for real (Spring, the adapters, PostgreSQL with Flyway, a transaction per attempt):
 * the journey of a new association from registration to its first booking, and the guards that need the
 * database (the last administrator, unique names, venues in use, append-only payments, concurrent payments).
 * Every test registers its own association, so tests do not disturb each other.
 */
@SpringBootTest
class AdminFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RegisterAssociationUseCase register;
    @Autowired
    private SubmitJoinRequestUseCase submit;
    @Autowired
    private ApproveJoinRequestUseCase approve;
    @Autowired
    private CreatePlanUseCase createPlan;
    @Autowired
    private AssignPlanUseCase assignPlan;
    @Autowired
    private RecordPaymentUseCase recordPayment;
    @Autowired
    private ReversePaymentUseCase reversePayment;
    @Autowired
    private MyPlanUseCase myPlan;
    @Autowired
    private EditPlanUseCase editPlan;
    @Autowired
    private MarkSubscriptionOverdueUseCase markOverdue;
    @Autowired
    private ListSubscriptionsByPaymentStatusUseCase listByStatus;
    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private DeleteVenueUseCase deleteVenue;
    @Autowired
    private CreateTrainingGroupUseCase createGroup;
    @Autowired
    private ArchiveTrainingGroupUseCase archiveGroup;
    @Autowired
    private GrantRoleUseCase grantRole;
    @Autowired
    private RevokeRoleUseCase revokeRole;
    @Autowired
    private DeactivateMemberUseCase deactivate;
    @Autowired
    private BookSessionUseCase book;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private MemberRepository members;
    @Autowired
    private JoinRequestRepository joinRequests;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private SessionRepository sessions;
    @Autowired
    private VenueRepository venues;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    @MockitoBean
    private Notifier notifier;

    // ------------------------------------------------------------------ fixtures

    private AssociationRegistered registered;
    private Actor founder;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private RegisterAssociationCommand registration(String shortName) {
        return new RegisterAssociationCommand("Club " + shortName, shortName, null, "Lisbon", "info@" + shortName + ".example",
                List.of("Beginner", "Intermediate", "Advanced"), "Ana Founder", "ana-" + unique() + "@example.com",
                "912345678", true, "2026-01");
    }

    private void givenARegisteredAssociation() {
        registered = register.execute(registration("club-" + unique()));
        founder = new Actor(registered.associationId(), registered.founderId());
    }

    private Association association() {
        return associations.findById(registered.associationId()).orElseThrow();
    }

    /** Someone asks to join and the founder approves: an active MEMBER at the entry level. */
    private Member joinedMember() {
        String email = "member-" + unique() + "@example.com";
        JoinRequestSubmitted submitted = submit.execute(new SubmitJoinRequestCommand(association().shortName().value(),
                "Rita Member", email, "912345678", true, "2026-01"));
        JoinRequestApproval approval = approve.execute(new ApproveJoinRequestCommand(founder, submitted.requestId()));
        return approval.member();
    }

    private Actor actorOf(Member member) {
        return new Actor(member.associationId(), member.id());
    }

    private Subscription assignedMonthly(Member member, long priceCents) {
        var plan = createPlan.execute(new CreatePlanCommand(founder, "Monthly " + unique(),
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(priceCents), null));
        return assignPlan.execute(new AssignPlanCommand(founder, member.id(), plan.id(), null));
    }

    private Subscription reload(Subscription subscription) {
        return subscriptions.findById(subscription.associationId(), subscription.id()).orElseThrow();
    }

    // -------------------------------------------------------------------- journey

    @Test
    void fromRegistrationToTheFirstBooking() {
        // Arrange
        givenARegisteredAssociation();
        Member founderMember = members.findById(registered.associationId(), registered.founderId()).orElseThrow();
        Venue venue = createVenue.execute(new CreateVenueCommand(founder, "Pavilhao Central", "Rua A 1", 2));
        Member coach = joinedMember();
        grantRole.execute(new GrantRoleCommand(founder, coach.id(), MemberRole.COACH));
        TrainingGroup group = createGroup.execute(new CreateTrainingGroupCommand(founder, "Tuesday group",
                Set.of(association().entryLevelId()), venue.id(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.TUESDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))),
                12, coach.id()));
        var start = clock.instant().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        Session session = sessions.save(Session.create(registered.associationId(), group.id(), coach.id(), start,
                start.plus(Duration.ofMinutes(90)), 12));
        Member player = joinedMember();

        // Act
        Subscription assigned = assignedMonthly(player, 3000);
        PaymentRecorded paid = recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(3000),
                clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate(), PaymentMethod.MB_WAY));
        PlacedBooking booked = book.execute(new BookSessionCommand(actorOf(player), session.id()));
        MyPlan plan = myPlan.execute(new MyPlanQuery(actorOf(player)));

        // Assert
        assertThat(founderMember.roles()).containsExactlyInAnyOrder(MemberRole.ADMIN, MemberRole.MEMBER);
        assertThat(founderMember.levelId()).isEqualTo(association().entryLevelId());
        assertThat(player.levelId()).isEqualTo(association().entryLevelId());
        assertThat(assigned.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paid.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(assigned).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).extracting(Payment::id)
                .containsExactly(paid.payment().id());
        assertThat(booked.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(plan.subscriptions()).singleElement().satisfies(entry -> {
            assertThat(entry.subscriptionId()).isEqualTo(assigned.id());
            assertThat(entry.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        });
        verify(notifier, org.mockito.Mockito.times(2)).memberApproved(org.mockito.ArgumentMatchers.eq(registered.associationId()),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aPartialPaymentKeepsThePendingStatusAndAReversalReopensAPaidSubscription() {
        // Arrange
        givenARegisteredAssociation();
        Member player = joinedMember();
        Subscription assigned = assignedMonthly(player, 3000);
        var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();

        // Act
        PaymentRecorded first = recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(1000), today, PaymentMethod.CASH));
        PaymentRecorded second = recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(2000), today, PaymentMethod.CASH));
        PaymentReversed reversed = reversePayment.execute(new ReversePaymentCommand(founder, second.payment().id()));

        // Assert
        assertThat(first.subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(second.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reversed.subscription().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reversed.outstanding()).isEqualTo(Money.ofCents(2000));
        assertThat(reload(assigned).paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).hasSize(3);
        assertThat(payments.findById(registered.associationId(), second.payment().id()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(second.payment());
        assertThat(listByStatus.execute(new ListSubscriptionsByPaymentStatusQuery(founder, PaymentStatus.PENDING)))
                .extracting(entry -> entry.subscription().id()).contains(assigned.id());
    }

    @Test
    void aPaymentCannotBeReversedTwiceNorAReversalReversed() {
        // Arrange
        givenARegisteredAssociation();
        Subscription assigned = assignedMonthly(joinedMember(), 3000);
        var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();
        PaymentRecorded paid = recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(3000), today, PaymentMethod.CASH));
        PaymentReversed reversed = reversePayment.execute(new ReversePaymentCommand(founder, paid.payment().id()));
        Executable twice = () -> reversePayment.execute(new ReversePaymentCommand(founder, paid.payment().id()));
        Executable reversal = () -> reversePayment.execute(new ReversePaymentCommand(founder, reversed.reversal().id()));

        // Act
        assertThrows(PaymentNotReversibleException.class, twice);
        assertThrows(PaymentNotReversibleException.class, reversal);

        // Assert
        assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).hasSize(2);
    }

    @Test
    void aPlainMemberCannotRecordPaymentsAndAnotherAssociationCannotSeeTheSubscription() {
        // Arrange
        givenARegisteredAssociation();
        Member player = joinedMember();
        Subscription assigned = assignedMonthly(player, 3000);
        var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();
        AssociationRegistered other = register.execute(registration("other-" + unique()));
        Actor foreignAdmin = new Actor(other.associationId(), other.founderId());
        Executable byMember = () -> recordPayment.execute(new RecordPaymentCommand(actorOf(player), assigned.id(),
                Money.ofCents(3000), today, PaymentMethod.CASH));
        Executable byForeignAdmin = () -> recordPayment.execute(new RecordPaymentCommand(foreignAdmin, assigned.id(),
                Money.ofCents(3000), today, PaymentMethod.CASH));

        // Act
        assertThrows(NotAllowedException.class, byMember);
        assertThrows(com.regivolley.api.domain.exception.SubscriptionNotFoundException.class, byForeignAdmin);

        // Assert
        assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).isEmpty();
    }


    @Test
    void editingAPlansPriceDoesNotChangeWhatAnExistingSubscriptionOwes() {
        // Arrange
        givenARegisteredAssociation();
        var plan = createPlan.execute(new CreatePlanCommand(founder, "Monthly " + unique(),
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null));
        Subscription sold = assignPlan.execute(new AssignPlanCommand(founder, joinedMember().id(), plan.id(), null));
        editPlan.execute(new EditPlanCommand(founder, plan.id(), plan.name(), plan.terms(), Money.ofCents(5000), null));
        Subscription soldAfter = assignPlan.execute(new AssignPlanCommand(founder, joinedMember().id(), plan.id(), null));
        var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();

        // Act
        PaymentRecorded paid = recordPayment.execute(new RecordPaymentCommand(founder, sold.id(), Money.ofCents(3000), today, PaymentMethod.CASH));
        Executable tooMuch = () -> recordPayment.execute(new RecordPaymentCommand(founder, soldAfter.id(), Money.ofCents(5001), today, PaymentMethod.CASH));

        // Assert
        assertThat(sold.price()).isEqualTo(Money.ofCents(3000));
        assertThat(reload(sold).price()).isEqualTo(Money.ofCents(3000));
        assertThat(paid.subscription().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(soldAfter.price()).isEqualTo(Money.ofCents(5000));
        assertThrows(PaymentExceedsOutstandingException.class, tooMuch);
    }

    @Test
    void anOverdueSubscriptionIsListedStopsBookingAndPayingItInFullRestoresBooking() {
        // Arrange
        givenARegisteredAssociation();
        Venue venue = createVenue.execute(new CreateVenueCommand(founder, "Pavilhao", "Rua A", 1));
        Member coach = joinedMember();
        grantRole.execute(new GrantRoleCommand(founder, coach.id(), MemberRole.COACH));
        TrainingGroup group = createGroup.execute(new CreateTrainingGroupCommand(founder, "Group",
                Set.of(association().entryLevelId()), venue.id(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.MONDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))), 12, coach.id()));
        var start = clock.instant().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        Session session = sessions.save(Session.create(registered.associationId(), group.id(), coach.id(), start,
                start.plus(Duration.ofMinutes(90)), 12));
        Member player = joinedMember();
        Subscription assigned = assignedMonthly(player, 3000);
        var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();

        // Act
        Subscription overdue = markOverdue.execute(new MarkSubscriptionOverdueCommand(founder, assigned.id()));
        var listed = listByStatus.execute(new ListSubscriptionsByPaymentStatusQuery(founder, PaymentStatus.OVERDUE));
        BookingNotAllowedException refused = assertThrows(BookingNotAllowedException.class,
                () -> book.execute(new BookSessionCommand(actorOf(player), session.id())));
        recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(3000), today, PaymentMethod.CASH));
        PlacedBooking booked = book.execute(new BookSessionCommand(actorOf(player), session.id()));

        // Assert
        assertThat(overdue.paymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(listed).extracting(entry -> entry.subscription().id()).containsExactly(assigned.id());
        assertThat(listed.get(0).memberName()).isEqualTo(player.name());
        assertThat(refused.reason()).isEqualTo(BookingRejectionReason.PAYMENT_OVERDUE);
        assertThat(booked.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void anInactiveMemberCannotBeGivenAPlan() {
        // Arrange
        givenARegisteredAssociation();
        Member leaving = joinedMember();
        deactivate.execute(new DeactivateMemberCommand(founder, leaving.id()));
        var plan = createPlan.execute(new CreatePlanCommand(founder, "Monthly " + unique(),
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null));
        Executable act = () -> assignPlan.execute(new AssignPlanCommand(founder, leaving.id(), plan.id(), null));

        // Act
        assertThrows(com.regivolley.api.domain.exception.MemberInactiveException.class, act);

        // Assert
        assertThat(subscriptions.findByMember(registered.associationId(), leaving.id())).isEmpty();
    }

    // ----------------------------------------------------------- uniqueness and guards

    @Test
    void aShortNameCanBeRegisteredOnlyOnce() {
        // Arrange
        givenARegisteredAssociation();
        String taken = association().shortName().value();
        Executable again = () -> register.execute(registration(taken));

        // Act
        ShortNameAlreadyTakenException ex = assertThrows(ShortNameAlreadyTakenException.class, again);

        // Assert
        assertThat(ex.shortName()).isEqualTo(ShortName.of(taken));
    }

    @Test
    void twoRegistrationsRacingForTheSameShortNameLeaveExactlyOneAssociation() throws Exception {
        // Arrange
        String contested = "race-" + unique();

        // Act
        List<Outcome> outcomes = raceAll(List.of(
                () -> register.execute(registration(contested)),
                () -> register.execute(registration(contested))));

        // Assert
        assertThat(outcomes).filteredOn(o -> o.failure() == null).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                .satisfies(o -> assertThat(o.failure()).isInstanceOf(ShortNameAlreadyTakenException.class));
        assertThat(associations.findByShortName(ShortName.of(contested))).isPresent();
    }

    @Test
    void aSecondRequestFromTheSameEmailIsRejectedGenericallyWhetherPendingOrAlreadyAMember() {
        // Arrange
        givenARegisteredAssociation();
        String shortName = association().shortName().value();
        submit.execute(new SubmitJoinRequestCommand(shortName, "Rita", "rita@example.com", "912345678", true, "2026-01"));
        Executable pending = () -> submit.execute(new SubmitJoinRequestCommand(shortName, "Rita", "rita@example.com",
                "912345678", true, "2026-01"));
        Member member = joinedMember();
        Executable existingMember = () -> submit.execute(new SubmitJoinRequestCommand(shortName, "Rita", member.email().value(),
                "912345678", true, "2026-01"));

        // Act
        JoinRequestNotPossibleException forPending = assertThrows(JoinRequestNotPossibleException.class, pending);
        JoinRequestNotPossibleException forMember = assertThrows(JoinRequestNotPossibleException.class, existingMember);

        // Assert
        assertThat(forPending.getMessage()).isEqualTo(forMember.getMessage());
        assertThat(joinRequests.findPending(registered.associationId())).hasSize(1);
    }

    @Test
    void theLastAdministratorCannotBeDeactivatedOrDemotedButAnotherCanBeOnceThereAreTwo() {
        // Arrange
        givenARegisteredAssociation();
        Member second = joinedMember();
        Executable deactivateLast = () -> deactivate.execute(new DeactivateMemberCommand(founder, founder.memberId()));
        Executable demoteLast = () -> revokeRole.execute(new RevokeRoleCommand(founder, founder.memberId(), MemberRole.ADMIN));

        // Act
        assertThrows(LastAdministratorException.class, deactivateLast);
        assertThrows(LastAdministratorException.class, demoteLast);
        grantRole.execute(new GrantRoleCommand(founder, second.id(), MemberRole.ADMIN));
        revokeRole.execute(new RevokeRoleCommand(founder, second.id(), MemberRole.ADMIN));
        grantRole.execute(new GrantRoleCommand(founder, second.id(), MemberRole.ADMIN));
        deactivate.execute(new DeactivateMemberCommand(actorOf(second), founder.memberId()));

        // Assert
        assertThat(members.findById(registered.associationId(), founder.memberId()).orElseThrow().status())
                .isEqualTo(MemberStatus.INACTIVE);
        assertThat(members.findById(registered.associationId(), second.id()).orElseThrow().status())
                .isEqualTo(MemberStatus.ACTIVE);
        Executable deactivateNowLast = () -> deactivate.execute(new DeactivateMemberCommand(actorOf(second), second.id()));
        assertThrows(LastAdministratorException.class, deactivateNowLast);
    }

    @Test
    void aVenueUsedByAnActiveGroupCannotBeDeletedUntilTheGroupIsArchived() {
        // Arrange
        givenARegisteredAssociation();
        Venue venue = createVenue.execute(new CreateVenueCommand(founder, "Pavilhao", "Rua A", 1));
        TrainingGroup group = createGroup.execute(new CreateTrainingGroupCommand(founder, "Group",
                Set.of(association().entryLevelId()), venue.id(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.MONDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))), 12,
                coachOf()));
        Executable blocked = () -> deleteVenue.execute(new DeleteVenueCommand(founder, venue.id()));

        // Act
        assertThrows(VenueInUseException.class, blocked);
        archiveGroup.execute(new ArchiveTrainingGroupCommand(founder, group.id()));
        deleteVenue.execute(new DeleteVenueCommand(founder, venue.id()));

        // Assert
        assertThat(venues.findById(registered.associationId(), venue.id())).isEmpty();
    }

    private com.regivolley.api.domain.model.valueobject.MemberId coachOf() {
        Member coach = joinedMember();
        grantRole.execute(new GrantRoleCommand(founder, coach.id(), MemberRole.COACH));
        return coach.id();
    }

    // ------------------------------------------------------------------ concurrency

    private int activeAdmins() {
        return jdbc.queryForObject("""
                select count(*) from members m join member_roles r on r.member_id = m.id
                where m.association_id = ? and m.status = 'ACTIVE' and r.role = 'ADMIN'
                """, Integer.class, registered.associationId().value());
    }

    /** What one contender of a race got: its result, or the failure that stopped it. */
    private record Outcome(Object result, Throwable failure) {
    }

    /** Runs the attempts at the same instant, each in its own thread and its own committed transactions. */
    private List<Outcome> raceAll(List<Callable<?>> attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        try {
            CountDownLatch ready = new CountDownLatch(attempts.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> attempt : attempts) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return attempt.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    outcomes.add(new Outcome(future.get(60, TimeUnit.SECONDS), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome(null, e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoAdministratorsDeactivatingEachOtherAtOnceLeaveOneActive() throws Exception {
        for (int round = 0; round < 6; round++) {
            // Arrange
            givenARegisteredAssociation();
            Member second = joinedMember();
            grantRole.execute(new GrantRoleCommand(founder, second.id(), MemberRole.ADMIN));
            Actor secondActor = actorOf(second);

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> deactivate.execute(new DeactivateMemberCommand(founder, second.id())),
                    () -> deactivate.execute(new DeactivateMemberCommand(secondActor, founder.memberId()))));

            // Assert
            assertThat(outcomes).as("round %d", round).filteredOn(o -> o.failure() == null).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                    .satisfies(o -> assertThat(o.failure()).isInstanceOfAny(LastAdministratorException.class, NotAllowedException.class));
            assertThat(activeAdmins()).isEqualTo(1);
        }
    }


    private static void assertOneWonAndTheOtherWasRefused(List<Outcome> outcomes, int round) {
        assertThat(outcomes).as("round %d", round).filteredOn(o -> o.failure() == null).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                .satisfies(o -> assertThat(o.failure()).isInstanceOfAny(LastAdministratorException.class, NotAllowedException.class));
    }

    @Test
    void twoAdministratorsRevokingEachOthersRoleAtOnceLeaveOneAdministrator() throws Exception {
        for (int round = 0; round < 8; round++) {
            // Arrange
            givenARegisteredAssociation();
            Member second = joinedMember();
            grantRole.execute(new GrantRoleCommand(founder, second.id(), MemberRole.ADMIN));
            Actor secondActor = actorOf(second);

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> revokeRole.execute(new RevokeRoleCommand(founder, second.id(), MemberRole.ADMIN)),
                    () -> revokeRole.execute(new RevokeRoleCommand(secondActor, founder.memberId(), MemberRole.ADMIN))));

            // Assert
            assertOneWonAndTheOtherWasRefused(outcomes, round);
            assertThat(activeAdmins()).isEqualTo(1);
        }
    }

    @Test
    void anAdministratorRevokedWhileTheOtherIsDeactivatedLeavesOneAdministrator() throws Exception {
        for (int round = 0; round < 8; round++) {
            // Arrange
            givenARegisteredAssociation();
            Member second = joinedMember();
            grantRole.execute(new GrantRoleCommand(founder, second.id(), MemberRole.ADMIN));
            Actor secondActor = actorOf(second);

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> revokeRole.execute(new RevokeRoleCommand(founder, second.id(), MemberRole.ADMIN)),
                    () -> deactivate.execute(new DeactivateMemberCommand(secondActor, founder.memberId()))));

            // Assert
            assertOneWonAndTheOtherWasRefused(outcomes, round);
            assertThat(activeAdmins()).isEqualTo(1);
        }
    }

    @Test
    void twoAdministratorsEachRevokingTheirOwnRoleAtOnceLeaveOneAdministrator() throws Exception {
        for (int round = 0; round < 8; round++) {
            // Arrange
            givenARegisteredAssociation();
            Member second = joinedMember();
            grantRole.execute(new GrantRoleCommand(founder, second.id(), MemberRole.ADMIN));
            Actor secondActor = actorOf(second);

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> revokeRole.execute(new RevokeRoleCommand(founder, founder.memberId(), MemberRole.ADMIN)),
                    () -> revokeRole.execute(new RevokeRoleCommand(secondActor, second.id(), MemberRole.ADMIN))));

            // Assert
            assertOneWonAndTheOtherWasRefused(outcomes, round);
            assertThat(activeAdmins()).isEqualTo(1);
        }
    }

    @Test
    void twoFullPaymentsRecordedAtOnceStoreExactlyOne() throws Exception {
        for (int round = 0; round < 6; round++) {
            // Arrange
            givenARegisteredAssociation();
            Subscription assigned = assignedMonthly(joinedMember(), 3000);
            var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(3000), today, PaymentMethod.CASH)),
                    () -> recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(3000), today, PaymentMethod.TRANSFER))));

            // Assert
            assertThat(outcomes).as("round %d", round).filteredOn(o -> o.failure() == null).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                    .satisfies(o -> assertThat(o.failure()).isInstanceOf(PaymentExceedsOutstandingException.class));
            assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).hasSize(1);
            assertThat(reload(assigned).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        }
    }


    @Test
    void twoPartialPaymentsThatTogetherExceedThePriceStoreExactlyOne() throws Exception {
        for (int round = 0; round < 6; round++) {
            // Arrange
            givenARegisteredAssociation();
            Subscription assigned = assignedMonthly(joinedMember(), 3000);
            var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(2000), today, PaymentMethod.CASH)),
                    () -> recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(2000), today, PaymentMethod.TRANSFER))));

            // Assert
            assertThat(outcomes).as("round %d", round).filteredOn(o -> o.failure() == null).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                    .satisfies(o -> assertThat(o.failure()).isInstanceOf(PaymentExceedsOutstandingException.class));
            assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).hasSize(1);
            assertThat(reload(assigned).paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Test
    void twoReversalsOfOnePaymentAtOnceStoreExactlyOne() throws Exception {
        for (int round = 0; round < 6; round++) {
            // Arrange
            givenARegisteredAssociation();
            Subscription assigned = assignedMonthly(joinedMember(), 3000);
            var today = clock.instant().atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate();
            PaymentRecorded paid = recordPayment.execute(new RecordPaymentCommand(founder, assigned.id(), Money.ofCents(3000), today, PaymentMethod.CASH));

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> reversePayment.execute(new ReversePaymentCommand(founder, paid.payment().id())),
                    () -> reversePayment.execute(new ReversePaymentCommand(founder, paid.payment().id()))));

            // Assert
            assertThat(outcomes).as("round %d", round).filteredOn(o -> o.failure() == null).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                    .satisfies(o -> assertThat(o.failure()).isInstanceOf(PaymentNotReversibleException.class));
            assertThat(payments.findBySubscription(registered.associationId(), assigned.id())).hasSize(2);
            assertThat(reload(assigned).paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }
}
