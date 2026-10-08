package com.regivolley.api.application;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.command.CancelBookingCommand;
import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.result.PlacedBooking;
import com.regivolley.api.application.result.SessionGenerationReport;
import com.regivolley.api.application.usecase.BookSessionUseCase;
import com.regivolley.api.application.usecase.CancelBookingUseCase;
import com.regivolley.api.application.usecase.DeactivateMemberUseCase;
import com.regivolley.api.application.usecase.GenerateSessionsUseCase;
import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.BookingOverlapException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.factory.TrainingGroupFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.api.infrastructure.config.SessionGenerationScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The booking use cases wired for real: Spring, the persistence adapters, PostgreSQL with Flyway and
 * a transaction per attempt. Each test works in its own association, so tests do not disturb each
 * other. Nothing here runs inside a test transaction: every use case commits, as in production, which
 * is the only way the races of architecture.md section 10 can be exercised.
 */
@SpringBootTest
class BookingFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BookSessionUseCase book;
    @Autowired
    private CancelBookingUseCase cancel;
    @Autowired
    private DeactivateMemberUseCase deactivate;
    @Autowired
    private GenerateSessionsUseCase generate;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private MemberRepository members;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private TrainingGroupRepository groups;
    @Autowired
    private SessionRepository sessions;
    @Autowired
    private Clock clock;
    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private Notifier notifier;

    // ------------------------------------------------------------------ fixtures

    private final AtomicInteger sessionCounter = new AtomicInteger();

    private Association association;
    private Member coach;
    private TrainingGroup group;

    private void givenAnAssociationWithAGroup() {
        association = associations.save(AssociationFactory.create("Club " + UUID.randomUUID(),
                "club-" + UUID.randomUUID().toString().substring(0, 12), null, "Lisbon", "info@club.example",
                List.of("Beginner", "Intermediate")));
        coach = members.save(newMember(MemberRole.COACH));
        group = groups.save(TrainingGroupFactory.create(association.id(), "Tuesday group",
                Set.of(association.entryLevelId()), VenueId.generate(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.TUESDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))),
                12, coach.id()));
    }

    private Member newMember(MemberRole role) {
        return MemberFactory.create(association, ContactDetails.of("Person " + UUID.randomUUID().toString().substring(0, 6),
                        EmailAddress.of(UUID.randomUUID() + "@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", clock), Set.of(role), clock);
    }

    /** A member with a paid 10-credit pack that covers the next two weeks. */
    private Member memberWithPack() {
        return memberWithPack(10);
    }

    private Member memberWithPack(int credits) {
        Member member = members.save(newMember(MemberRole.MEMBER));
        Plan plan = plans.save(PlanFactory.create(association.id(), "Pack of " + credits, PlanTerms.pack(credits, Set.of()),
                Money.ofCents(4500), 90));
        LocalDate yesterday = clock.instant().atZone(ScheduleZone.LISBON.zoneId()).toLocalDate().minusDays(1);
        subscriptions.save(SubscriptionFactory.create(plan, member.id(), yesterday, List.of()).markPaid());
        return member;
    }

    private Session newSession(int capacity, Instant start) {
        return sessions.save(SessionFactory.create(association.id(), group.id(), coach.id(), start,
                start.plus(Duration.ofMinutes(90)), capacity));
    }

    /** Each call starts one second later than the last, so two sessions of the group never share a start. */
    private Session sessionInTwoDays(int capacity) {
        return newSession(capacity, clock.instant().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(sessionCounter.incrementAndGet()));
    }

    private Actor actor(Member member) {
        return new Actor(association.id(), member.id());
    }

    private int creditsUsed(Member member) {
        return subscriptions.findByMember(association.id(), member.id()).stream().mapToInt(Subscription::creditsUsed).sum();
    }

    private Session reload(Session session) {
        return sessions.findById(association.id(), session.id()).orElseThrow();
    }

    private static BookingStatus statusOf(Session session, Member member) {
        return session.bookings().stream().filter(b -> b.memberId().equals(member.id())).findFirst().orElseThrow().status();
    }

    // ------------------------------------------------------------------- tests

    @Test
    void bookFullWaitlistCancelPromotionIsChargedAndNotified() {
        // Arrange
        givenAnAssociationWithAGroup();
        Session session = sessionInTwoDays(1);
        Member first = memberWithPack();
        Member second = memberWithPack();

        // Act
        PlacedBooking firstBooking = book.execute(new BookSessionCommand(actor(first), session.id()));
        PlacedBooking secondBooking = book.execute(new BookSessionCommand(actor(second), session.id()));
        var cancelled = cancel.execute(new CancelBookingCommand(actor(first), session.id(), firstBooking.booking().id()));

        // Assert
        assertThat(firstBooking.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(secondBooking.booking().status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(secondBooking.waitlistPosition()).hasValue(1);
        assertThat(cancelled.creditRefunded()).isTrue();
        assertThat(cancelled.promoted()).extracting(Booking::memberId).containsExactly(second.id());
        Session stored = reload(session);
        assertThat(statusOf(stored, first)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(statusOf(stored, second)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(creditsUsed(first)).isZero();
        assertThat(creditsUsed(second)).isEqualTo(1);
        verify(notifier).bookingPromoted(association.id(), session.id(), second.id());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void aMemberWithoutASubscriptionIsRejectedWithTheReasonAndNothingIsStored() {
        // Arrange
        givenAnAssociationWithAGroup();
        Session session = sessionInTwoDays(5);
        Member broke = members.save(newMember(MemberRole.MEMBER));

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class,
                () -> book.execute(new BookSessionCommand(actor(broke), session.id())));

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        assertThat(reload(session).bookings()).isEmpty();
    }

    @Test
    void aMemberCannotHoldTwoOverlappingSessions() {
        // Arrange
        givenAnAssociationWithAGroup();
        Instant start = clock.instant().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        Session first = newSession(5, start);
        Session overlapping = newSession(5, start.plus(Duration.ofMinutes(30)));
        Member member = memberWithPack();
        book.execute(new BookSessionCommand(actor(member), first.id()));

        // Act
        BookingOverlapException ex = assertThrows(BookingOverlapException.class,
                () -> book.execute(new BookSessionCommand(actor(member), overlapping.id())));

        // Assert
        assertThat(ex.overlappingSessionId()).isEqualTo(first.id());
        assertThat(reload(overlapping).bookings()).isEmpty();
        assertThat(creditsUsed(member)).isEqualTo(1);
    }

    @Test
    void anotherAssociationsAdministratorCannotTouchTheSession() {
        // Arrange
        givenAnAssociationWithAGroup();
        Session session = sessionInTwoDays(5);
        Member member = memberWithPack();
        PlacedBooking placed = book.execute(new BookSessionCommand(actor(member), session.id()));
        Association other = associations.save(AssociationFactory.create("Other " + UUID.randomUUID(),
                "other-" + UUID.randomUUID().toString().substring(0, 12), null, "Porto", "x@y.example", List.of("Beginner")));
        Member foreignAdmin = members.save(MemberFactory.create(other, ContactDetails.of("Admin",
                        EmailAddress.of(UUID.randomUUID() + "@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", clock), Set.of(MemberRole.ADMIN), clock));

        // Act
        Executable attempt = () -> cancel.execute(new CancelBookingCommand(
                new Actor(other.id(), foreignAdmin.id()), session.id(), placed.booking().id()));

        // Assert
        assertThrows(SessionNotFoundException.class, attempt);
        assertThat(statusOf(reload(session), member)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void generatingSessionsTwiceCreatesThemOnceAndNeverRevivesACancelledOne() {
        // Arrange
        givenAnAssociationWithAGroup();
        var command = new GenerateSessionsCommand(association.id());

        // Act
        SessionGenerationReport first = generate.execute(command);
        Instant windowEnd = clock.instant().plus(Duration.ofDays(40));
        List<Session> created = sessions.findByTrainingGroupStartingBetween(association.id(), group.id(),
                clock.instant(), windowEnd);
        sessions.save(created.get(0).cancel("Venue closed"));
        SessionGenerationReport second = generate.execute(command);

        // Assert
        assertThat(first.sessionsCreated()).isBetween(3, 5);
        assertThat(second.sessionsCreated()).isZero();
        assertThat(sessions.findByTrainingGroupStartingBetween(association.id(), group.id(), clock.instant(), windowEnd))
                .hasSize(first.sessionsCreated());
    }

    @Test
    void deactivatingAMemberFreesTheirSeatPromotesTheQueueAndRefundsTheCredit() {
        // Arrange
        givenAnAssociationWithAGroup();
        Member admin = members.save(newMember(MemberRole.ADMIN));
        Session session = sessionInTwoDays(1);
        Member leaving = memberWithPack();
        Member waiting = memberWithPack();
        book.execute(new BookSessionCommand(actor(leaving), session.id()));
        book.execute(new BookSessionCommand(actor(waiting), session.id()));

        // Act
        var result = deactivate.execute(new DeactivateMemberCommand(actor(admin), leaving.id()));

        // Assert
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        assertThat(members.findById(association.id(), leaving.id()).orElseThrow().status()).isEqualTo(MemberStatus.INACTIVE);
        Session stored = reload(session);
        assertThat(statusOf(stored, leaving)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(statusOf(stored, waiting)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(creditsUsed(leaving)).isZero();
        assertThat(creditsUsed(waiting)).isEqualTo(1);
        verify(notifier).bookingPromoted(association.id(), session.id(), waiting.id());
    }

    @Test
    void aPlainMemberCannotDeactivateAnyone() {
        // Arrange
        givenAnAssociationWithAGroup();
        Member member = memberWithPack();
        Member other = memberWithPack();

        // Act
        Executable attempt = () -> deactivate.execute(new DeactivateMemberCommand(actor(member), other.id()));

        // Assert
        assertThrows(NotAllowedException.class, attempt);
        assertThat(members.findById(association.id(), other.id()).orElseThrow().status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void theDailySchedulerIsNotRunningInTests() {
        // Arrange
        // (the test configuration switches it off)

        // Act
        String[] beans = context.getBeanNamesForType(SessionGenerationScheduler.class);

        // Assert
        assertThat(beans).isEmpty();
    }

    // ------------------------------------------------------------- concurrency

    /** Runs the bookings at the same instant, each in its own thread and transaction; returns what each one got. */
    private List<PlacedBooking> bookTogether(Session session, List<Member> contenders) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(contenders.size());
        try {
            CountDownLatch ready = new CountDownLatch(contenders.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<PlacedBooking>> futures = new ArrayList<>();
            for (Member contender : contenders) {
                Callable<PlacedBooking> task = () -> {
                    ready.countDown();
                    go.await();
                    return book.execute(new BookSessionCommand(actor(contender), session.id()));
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<PlacedBooking> results = new ArrayList<>();
            for (Future<PlacedBooking> future : futures) {
                try {
                    results.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    throw new AssertionError("A contender failed instead of being confirmed or waitlisted", e.getCause());
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoMembersRacingForTheLastSeatThroughTheUseCaseOneIsConfirmedAndTheOtherWaitlisted() throws Exception {
        givenAnAssociationWithAGroup();
        for (int round = 0; round < 10; round++) {
            // Arrange
            Session session = sessionInTwoDays(1);
            List<Member> contenders = List.of(memberWithPack(), memberWithPack());

            // Act
            List<PlacedBooking> results = bookTogether(session, contenders);

            // Assert
            assertThat(results).extracting(placed -> placed.booking().status())
                    .as("round %d", round)
                    .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED);
            Session stored = reload(session);
            assertThat(stored.confirmedCount()).isEqualTo(1);
            assertThat(stored.waitlist()).hasSize(1);
            Member winner = contenders.stream().filter(m -> statusOf(stored, m) == BookingStatus.CONFIRMED).findFirst().orElseThrow();
            Member loser = contenders.stream().filter(m -> statusOf(stored, m) == BookingStatus.WAITLISTED).findFirst().orElseThrow();
            assertThat(creditsUsed(winner)).isEqualTo(1);
            assertThat(creditsUsed(loser)).isZero();
        }
    }

    @Test
    void fourMembersRacingForTwoSeatsFillExactlyTwoAndQueueTheRest() throws Exception {
        // Arrange
        givenAnAssociationWithAGroup();
        Session session = sessionInTwoDays(2);
        List<Member> contenders = IntStream.range(0, 4).mapToObj(i -> memberWithPack()).toList();

        // Act
        List<PlacedBooking> results = bookTogether(session, contenders);

        // Assert
        assertThat(results).extracting(placed -> placed.booking().status())
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.CONFIRMED,
                        BookingStatus.WAITLISTED, BookingStatus.WAITLISTED);
        Session stored = reload(session);
        assertThat(stored.confirmedCount()).isEqualTo(2);
        assertThat(contenders.stream().mapToInt(this::creditsUsed).sum()).isEqualTo(2);
    }

    /** What one contender of a race got: the booking, or the failure that stopped it. */
    private record Outcome(PlacedBooking placed, Throwable failure) {
    }

    /** Runs the attempts at the same instant, each in its own thread and its own committed transactions. */
    private List<Outcome> raceAll(List<Callable<PlacedBooking>> attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        try {
            CountDownLatch ready = new CountDownLatch(attempts.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<PlacedBooking>> futures = new ArrayList<>();
            for (Callable<PlacedBooking> attempt : attempts) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return attempt.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<PlacedBooking> future : futures) {
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
    void oneMembersLastCreditBookedOnTwoSessionsAtOnceIsSpentOnceAndTheLoserStoresNothing() throws Exception {
        givenAnAssociationWithAGroup();
        for (int round = 0; round < 6; round++) {
            // Arrange - two sessions a day apart: no overlap, the only contention is the single credit
            Session first = sessionInTwoDays(5);
            Session second = newSession(5, first.startsAt().plus(Duration.ofDays(1)));
            Member member = memberWithPack(1);

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> book.execute(new BookSessionCommand(actor(member), first.id())),
                    () -> book.execute(new BookSessionCommand(actor(member), second.id()))));

            // Assert
            assertThat(outcomes).filteredOn(o -> o.placed() != null).as("round %d winners", round).hasSize(1);
            Outcome rejected = outcomes.stream().filter(o -> o.failure() != null).findFirst().orElseThrow();
            assertThat(rejected.failure()).isInstanceOf(BookingNotAllowedException.class);
            assertThat(((BookingNotAllowedException) rejected.failure()).reason()).isEqualTo(BookingRejectionReason.NO_BALANCE);
            Session winnerSession = outcomes.stream().filter(o -> o.placed() != null).findFirst().orElseThrow().placed().session();
            Session loserSession = winnerSession.id().equals(first.id()) ? second : first;
            assertThat(statusOf(reload(winnerSession), member)).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(reload(loserSession).bookings()).isEmpty();
            assertThat(creditsUsed(member)).isEqualTo(1);
        }
    }

    @Test
    void oneMemberJoiningTwoOverlappingWaitlistsAtOnceGetsExactlyOneOfThem() throws Exception {
        givenAnAssociationWithAGroup();
        for (int round = 0; round < 6; round++) {
            // Arrange - both sessions are full, so the member only queues: no credit is touched, and only
            // the member's row lock can stop the two requests from both passing the overlap check
            Session first = sessionInTwoDays(1);
            Session second = newSession(1, first.startsAt().plus(Duration.ofMinutes(30)));
            book.execute(new BookSessionCommand(actor(memberWithPack()), first.id()));
            book.execute(new BookSessionCommand(actor(memberWithPack()), second.id()));
            Member member = memberWithPack();

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> book.execute(new BookSessionCommand(actor(member), first.id())),
                    () -> book.execute(new BookSessionCommand(actor(member), second.id()))));

            // Assert
            assertThat(outcomes).filteredOn(o -> o.placed() != null).as("round %d winners", round).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement()
                    .satisfies(o -> assertThat(o.failure()).isInstanceOf(BookingOverlapException.class));
            long queued = Stream.of(first, second).filter(session -> reload(session).waitlist().size() == 1).count();
            assertThat(queued).isEqualTo(1);
            assertThat(creditsUsed(member)).isZero();
        }
    }

    @Test
    void oneMemberBookingTwoOverlappingSessionsAtOnceGetsExactlyOneOfThem() throws Exception {
        givenAnAssociationWithAGroup();
        for (int round = 0; round < 6; round++) {
            // Arrange - plenty of credit; the sessions overlap by half an hour
            Session first = sessionInTwoDays(5);
            Session second = newSession(5, first.startsAt().plus(Duration.ofMinutes(30)));
            Member member = memberWithPack(10);

            // Act
            List<Outcome> outcomes = raceAll(List.of(
                    () -> book.execute(new BookSessionCommand(actor(member), first.id())),
                    () -> book.execute(new BookSessionCommand(actor(member), second.id()))));

            // Assert
            assertThat(outcomes).filteredOn(o -> o.placed() != null).as("round %d winners", round).hasSize(1);
            Outcome rejected = outcomes.stream().filter(o -> o.failure() != null).findFirst().orElseThrow();
            assertThat(rejected.failure()).isInstanceOf(BookingOverlapException.class);
            long stored = Stream.of(first, second).filter(session -> !reload(session).bookings().isEmpty()).count();
            assertThat(stored).isEqualTo(1);
            assertThat(creditsUsed(member)).isEqualTo(1);
        }
    }
}
