package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.Actor;
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
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Domain objects for the use case tests. "Now" is Monday 12 Oct 2026, 10:00 in Lisbon (summer time). */
final class Data {

    static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    /** Two days ahead: the booking window (7 days) is open and free cancellation (until 6 h before) is still on. */
    static final Instant SESSION_START = NOW.plus(Duration.ofDays(2));
    static final BookingPolicy POLICY = BookingPolicy.defaults();

    private Data() {
    }

    static Association association() {
        return AssociationFactory.create("Club", "club-" + UUID.randomUUID().toString().substring(0, 8), null, "Lisbon",
                "info@club.example", List.of("Beginner", "Intermediate", "Advanced"));
    }

    static LevelId level(Association association, String name) {
        return association.levels().stream().filter(l -> l.name().equals(name)).findFirst().orElseThrow().id();
    }

    /** An active member of the association at the entry level (Beginner) holding the given roles (MEMBER if none). */
    static Member member(Association association, MemberRole... roles) {
        Set<MemberRole> held = roles.length == 0 ? Set.of(MemberRole.MEMBER) : Set.of(roles);
        return MemberFactory.create(association, ContactDetails.of("Person " + UUID.randomUUID().toString().substring(0, 6),
                        EmailAddress.of(UUID.randomUUID().toString().substring(0, 8) + "@example.com"),
                        PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), held, CLOCK);
    }

    static Member atLevel(Member member, Association association, String levelName) {
        return member.changeLevel(association, level(association, levelName), member.id(), CLOCK);
    }

    static Member coach(Association association) {
        return member(association, MemberRole.COACH);
    }

    static Member admin(Association association) {
        return member(association, MemberRole.ADMIN);
    }

    static Actor actor(Member member) {
        return new Actor(member.associationId(), member.id());
    }

    static TrainingGroup group(Association association, Member coach, String... levelNames) {
        Set<LevelId> accepted = Stream.of(levelNames).map(name -> level(association, name)).collect(Collectors.toSet());
        return TrainingGroupFactory.create(association.id(), "Group", accepted, VenueId.generate(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))),
                12, coach.id());
    }

    static Session session(TrainingGroup group, Instant start, int capacity) {
        return SessionFactory.create(group.associationId(), group.id(), group.coachId(), start,
                start.plus(Duration.ofMinutes(90)), capacity);
    }

    static Session session(TrainingGroup group, int capacity) {
        return session(group, SESSION_START, capacity);
    }

    /** The session with {@code member} booked, as of {@code at}; the booking is CONFIRMED or WAITLISTED as the session decides. */
    static Session booked(Session session, Member member, Instant at) {
        return session.book(member.id(), POLICY, Clock.fixed(at, ZoneOffset.UTC)).session();
    }

    static Session booked(Session session, Member member) {
        return booked(session, member, NOW);
    }

    static Booking bookingOf(Session session, Member member) {
        return session.bookings().stream().filter(b -> b.memberId().equals(member.id())).findFirst().orElseThrow();
    }

    /** A paid, unlimited monthly subscription covering the whole of October 2026. */
    static Subscription unlimited(Association association, Member member) {
        Plan plan = PlanFactory.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        return SubscriptionFactory.create(plan, member.id(), LocalDate.parse("2026-10-01"), List.of()).markPaid();
    }

    static Subscription pack(Association association, Member member, int credits) {
        Plan plan = PlanFactory.create(association.id(), "Pack", PlanTerms.pack(credits, Set.of()), Money.ofCents(4500), 90);
        return SubscriptionFactory.create(plan, member.id(), LocalDate.parse("2026-10-01"), List.of()).markPaid();
    }

    /** A pack that only gives access to the listed levels (RN-14). */
    static Subscription packFor(Association association, Member member, int credits, LevelId... allowed) {
        Plan plan = PlanFactory.create(association.id(), "Pack", PlanTerms.pack(credits, Set.of(allowed)), Money.ofCents(4500), 90);
        return SubscriptionFactory.create(plan, member.id(), LocalDate.parse("2026-10-01"), List.of()).markPaid();
    }

    /** The subscription with its balance already spent on {@code booking} (as if it had been confirmed). */
    static Subscription charged(Subscription subscription, Session session, Booking booking) {
        return subscription.consume(booking.id(), session.startsAt());
    }
}
