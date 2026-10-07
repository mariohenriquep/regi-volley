package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
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
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
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

/** Domain objects for the persistence tests. Instants are whole seconds, which PostgreSQL stores exactly. */
public final class Fixtures {

    /** "Now" in every test: sessions are 2 days ahead, so the booking window (7 days) is open. */
    public static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final BookingPolicy POLICY = BookingPolicy.defaults();
    public static final Instant SESSION_START = NOW.plus(Duration.ofDays(2));

    private Fixtures() {
    }

    public static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Association association() {
        return association(unique("club"));
    }

    public static Association association(String shortName) {
        return Association.create("Club " + shortName, shortName, "123456789", "Lisbon",
                "info@" + shortName + ".example", List.of("Beginner", "Intermediate", "Advanced"));
    }

    public static ContactDetails contact(String name) {
        return ContactDetails.of(name, EmailAddress.of(unique("person") + "@example.com"), PhoneNumber.of("912345678"));
    }

    public static GdprConsent consent() {
        return GdprConsent.record(true, "2026-01", NOW);
    }

    public static Member member(Association association, String name) {
        return Member.create(association, contact(name), consent(), Set.of(MemberRole.MEMBER), CLOCK);
    }

    public static JoinRequest joinRequest(AssociationId associationId, String name, Instant requestedAt) {
        return JoinRequest.create(associationId, contact(name), true, "2026-01", at(requestedAt));
    }

    public static WeeklySlot slot(DayOfWeek day, int hour, int minutes) {
        return new WeeklySlot(day, LocalTime.of(hour, 0), Duration.ofMinutes(minutes));
    }

    public static TrainingGroup group(AssociationId associationId, String name, Set<LevelId> levels, MemberId coach) {
        return TrainingGroup.create(associationId, name, levels, VenueId.generate(),
                WeeklySchedule.of(slot(DayOfWeek.MONDAY, 20, 90), slot(DayOfWeek.WEDNESDAY, 21, 60)), 12, coach);
    }

    public static Session session(AssociationId associationId, TrainingGroupId groupId, MemberId coach, Instant start, int capacity) {
        return Session.create(associationId, groupId, coach, start, start.plus(Duration.ofMinutes(90)), capacity);
    }

    public static Session session(AssociationId associationId, Instant start, int capacity) {
        return session(associationId, TrainingGroupId.generate(), MemberId.generate(), start, capacity);
    }

    /** Books {@code member} {@code offsetSeconds} after {@link #NOW}, so arrival order is explicit. */
    public static Session book(Session session, MemberId member, long offsetSeconds) {
        return session.book(member, POLICY, at(NOW.plusSeconds(offsetSeconds))).session();
    }

    public static Plan pack(AssociationId associationId, Set<LevelId> allowedLevels) {
        return Plan.create(associationId, "Pack of 10", PlanTerms.pack(10, allowedLevels), Money.ofCents(4500), 90);
    }

    public static Plan monthlyNPerWeek(AssociationId associationId) {
        return Plan.create(associationId, "Twice a week", PlanTerms.monthlyNPerWeek(2, Set.of()), Money.ofCents(3000), null);
    }

    public static Subscription subscription(Plan plan, MemberId memberId, String startDate) {
        return Subscription.create(plan, memberId, LocalDate.parse(startDate), List.of());
    }
}
