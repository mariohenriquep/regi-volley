package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.factory.TrainingGroupFactory;
import com.regivolley.api.domain.factory.VenueFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
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

/** Domain objects the controller tests hand back from the mocked use cases. */
final class WebFixtures {

    static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private WebFixtures() {
    }

    static Association association() {
        return AssociationFactory.create("Club", "club-web", null, "Lisbon", "info@club.example", List.of("Beginner", "Intermediate"));
    }

    static Member member(Association association, MemberRole... roles) {
        return MemberFactory.create(association,
                ContactDetails.of("Ana Silva", EmailAddress.of("ana.silva@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), roles.length == 0 ? Set.of(MemberRole.MEMBER) : Set.of(roles), CLOCK);
    }

    static Venue venue(Association association) {
        return VenueFactory.create(association.id(), "Pavilion One", "Rua A 1, Lisbon", 2);
    }

    static TrainingGroup group(Association association, Venue venue, MemberId coach) {
        return TrainingGroupFactory.create(association.id(), "Wednesday Beginners", Set.of(association.entryLevelId()), venue.id(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))), 12, coach);
    }

    static Plan pack(Association association) {
        return PlanFactory.create(association.id(), "Ten sessions", PlanTerms.pack(10, Set.of(association.entryLevelId())), Money.ofCents(4500), 90);
    }

    static Plan monthly(Association association) {
        return PlanFactory.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
    }

    static Subscription subscription(Plan plan, MemberId memberId) {
        return SubscriptionFactory.create(plan, memberId, LocalDate.parse("2026-10-12"), List.of());
    }

    static Payment payment(Subscription subscription, MemberId recordedBy) {
        return PaymentFactory.create(subscription, Money.ofCents(1500), LocalDate.parse("2026-10-12"), PaymentMethod.CASH, recordedBy, CLOCK);
    }

    static Session session(Association association, TrainingGroup group) {
        Instant start = NOW.plus(Duration.ofDays(2));
        return SessionFactory.create(association.id(), group.id(), group.coachId(), start, start.plus(Duration.ofMinutes(90)), 12);
    }

    static Session bookedSession(Association association, TrainingGroup group, MemberId memberId) {
        return session(association, group).book(memberId, BookingPolicy.defaults(), CLOCK).session();
    }

    static JoinRequest joinRequest(Association association) {
        return JoinRequestFactory.create(association.id(), ContactDetails.of("Rita Costa", EmailAddress.of("rita@example.com"), PhoneNumber.of("912345679")), GdprConsent.record(true, "2026-01", CLOCK), CLOCK);
    }
}
