package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.InvalidPaymentStatusTransitionException;
import com.regivolley.api.domain.exception.InvalidSubscriptionException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.CreditUsage;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.shared.AggregateRoot;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A member's plan over a period (RN-16, US-20, US-23): start and end dates (both inclusive), a
 * snapshot of the plan's terms, the payment status (RN-18) and the bookings that currently hold
 * a place in its balance (RN-15). It is assigned, renewed and reconstituted only by {@code SubscriptionFactory},
 * which also owns the no-overlap rule (RN-16) and the price snapshot.
 *
 * <p>Immutable: every change returns a new instance. Everything is judged against the
 * <em>session's</em> Europe/Lisbon calendar date, never "today": validity, pack credits and the
 * weekly allowance (ISO week, Monday to Sunday).
 *
 * <p><b>Idempotency per booking:</b> {@link #consume} and {@link #refund} are keyed by
 * {@link BookingId}. Consuming a booking that already holds a place, or refunding one that
 * doesn't, is a no-op returning the same instance (never an error and never a second
 * charge/refund), so a retried use case is safe.
 */
public final class Subscription implements AggregateRoot {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PAID, PaymentStatus.OVERDUE),
            PaymentStatus.OVERDUE, EnumSet.of(PaymentStatus.PAID),
            PaymentStatus.PAID, EnumSet.of(PaymentStatus.PENDING)
    );

    private final SubscriptionId id;
    private final AssociationId associationId;
    private final MemberId memberId;
    private final PlanId planId;
    private final PlanTerms terms;
    private final Money price;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final PaymentStatus paymentStatus;
    private final List<CreditUsage> usages;
    private final long version;

    /**
     * Checks every invariant, so no subscription exists in an invalid state. Public because the only callers are
     * {@code SubscriptionFactory} (new and renewed subscriptions, persisted ones) and this class; the architecture
     * test pins that.
     *
     * @param version the optimistic-lock version it was loaded with (0 for a new subscription); every change
     *                carries it over unchanged, so a stale copy is detected when it is saved. It is what stops two
     *                concurrent bookings of one member from spending the same last credit (architecture.md section 10)
     */
    public Subscription(SubscriptionId id, AssociationId associationId, MemberId memberId, PlanId planId,
                        PlanTerms terms, Money price, LocalDate startDate, LocalDate endDate,
                        PaymentStatus paymentStatus, List<CreditUsage> usages, long version) {
        Objects.requireNonNull(terms, "terms must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(paymentStatus, "paymentStatus must not be null");
        Objects.requireNonNull(usages, "usages must not be null");
        if (endDate.isBefore(startDate)) {
            throw new InvalidSubscriptionException("A subscription cannot end before it starts");
        }
        if (version < 0) {
            throw new InvalidSubscriptionException("The version must not be negative");
        }
        requireConsistentUsages(terms, startDate, endDate, usages);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.associationId = Objects.requireNonNull(associationId, "associationId must not be null");
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.planId = Objects.requireNonNull(planId, "planId must not be null");
        this.terms = terms;
        this.price = price;
        this.startDate = startDate;
        this.endDate = endDate;
        this.paymentStatus = paymentStatus;
        this.usages = List.copyOf(usages);
        this.version = version;
    }

    private static void requireConsistentUsages(PlanTerms terms, LocalDate startDate, LocalDate endDate,
                                                List<CreditUsage> usages) {
        Set<BookingId> seen = new HashSet<>();
        for (CreditUsage usage : usages) {
            if (!seen.add(usage.bookingId())) {
                throw new InvalidSubscriptionException("A booking can hold only one place in a subscription");
            }
            if (usage.sessionDate().isBefore(startDate) || usage.sessionDate().isAfter(endDate)) {
                throw new InvalidSubscriptionException("A session outside the subscription period cannot use it");
            }
        }
        if (terms.credits() != null && usages.size() > terms.credits()) {
            throw new InvalidSubscriptionException("More credits used than the plan grants");
        }
        if (terms.sessionsPerWeek() != null && usages.stream()
                .collect(Collectors.groupingBy(usage -> startOfWeek(usage.sessionDate()), Collectors.counting()))
                .values().stream().anyMatch(count -> count > terms.sessionsPerWeek())) {
            throw new InvalidSubscriptionException("More sessions used in a week than the plan allows");
        }
    }

    // ------------------------------------------------------------ payment (RN-18)

    /** PENDING | OVERDUE -> PAID: a payment was registered (RN-17). */
    public Subscription markPaid() {
        return transitionTo(PaymentStatus.PAID);
    }

    /** PENDING -> OVERDUE: the payment is late. */
    public Subscription markOverdue() {
        return transitionTo(PaymentStatus.OVERDUE);
    }

    /**
     * PAID -> PENDING: a payment was reversed and money is due again (RN-19). A paid subscription never goes
     * back any other way, and never to OVERDUE directly: lateness is judged separately.
     */
    public Subscription reopenPayment() {
        return transitionTo(PaymentStatus.PENDING);
    }

    private Subscription transitionTo(PaymentStatus target) {
        if (!ALLOWED_TRANSITIONS.get(paymentStatus).contains(target)) {
            throw new InvalidPaymentStatusTransitionException(paymentStatus, target);
        }
        return new Subscription(id, associationId, memberId, planId, terms, price, startDate, endDate, target, usages, version);
    }

    // ------------------------------------------------------- balance (RN-06, RN-15)

    /**
     * Why this subscription cannot pay for a booking in a group accepting {@code groupLevels} at
     * {@code sessionStart}, or empty if it can. Checked in this order, so the reason reported is
     * the one the member cannot fix by paying: outside the period, plan level (RN-14), no
     * balance, overdue payment (RN-18).
     */
    public Optional<BookingRejectionReason> rejectionFor(Instant sessionStart, Set<LevelId> groupLevels) {
        return rejectionFor(sessionStart, groupLevels, List.of());
    }

    /**
     * As {@link #rejectionFor(Instant, Set)}, but an "N per week" allowance is shared with the
     * member's other weekly subscriptions in {@code memberSubscriptions} (RN-16): their usages in
     * the session's week count too. This is what {@code BookingEligibility} uses.
     */
    public Optional<BookingRejectionReason> rejectionFor(Instant sessionStart, Set<LevelId> groupLevels,
                                                         Collection<Subscription> memberSubscriptions) {
        Objects.requireNonNull(groupLevels, "groupLevels must not be null");
        Objects.requireNonNull(memberSubscriptions, "memberSubscriptions must not be null");
        LocalDate sessionDate = sessionDateOf(sessionStart);
        if (!isValidOn(sessionDate)) {
            return Optional.of(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        }
        if (!terms.allowsAnyOf(groupLevels)) {
            return Optional.of(BookingRejectionReason.PLAN_LEVEL_NOT_ALLOWED);
        }
        return balanceOrPaymentRejection(sessionDate, memberSubscriptions);
    }

    private Optional<BookingRejectionReason> balanceOrPaymentRejection(LocalDate sessionDate,
                                                                       Collection<Subscription> memberSubscriptions) {
        OptionalInt remaining = balanceOn(sessionDate, memberSubscriptions);
        if (remaining.isPresent() && remaining.getAsInt() < 1) {
            return Optional.of(BookingRejectionReason.NO_BALANCE);
        }
        if (paymentStatus == PaymentStatus.OVERDUE) {
            return Optional.of(BookingRejectionReason.PAYMENT_OVERDUE);
        }
        return Optional.empty();
    }

    /**
     * Charges one place to {@code bookingId} when its booking is confirmed (RN-15). Idempotent:
     * a booking that already holds a place returns this subscription unchanged. Otherwise the
     * session must fall in the period with balance left and the payment not overdue; plan levels
     * are the job of {@code BookingEligibility}, which runs before booking.
     *
     * <p><b>Contract:</b> this overload judges the weekly allowance of this subscription alone.
     * A use case that has the member's subscriptions (it always does after running
     * {@code BookingEligibility}) must use {@link #consume(BookingId, Instant, Collection)}, so the
     * charge is checked by exactly the rule eligibility applied and cannot succeed where
     * eligibility refused.
     *
     * @throws BookingNotAllowedException NO_VALID_SUBSCRIPTION, NO_BALANCE or PAYMENT_OVERDUE
     */
    public Subscription consume(BookingId bookingId, Instant sessionStart) {
        return consume(bookingId, sessionStart, List.of());
    }

    /**
     * As {@link #consume(BookingId, Instant)}, with an "N per week" allowance shared across the
     * member's weekly subscriptions in {@code memberSubscriptions} (RN-16); this subscription
     * and other members' are ignored if present in it.
     */
    public Subscription consume(BookingId bookingId, Instant sessionStart,
                                Collection<Subscription> memberSubscriptions) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(memberSubscriptions, "memberSubscriptions must not be null");
        if (holdsPlaceFor(bookingId)) {
            return this;
        }
        LocalDate sessionDate = sessionDateOf(sessionStart);
        Optional<BookingRejectionReason> rejection = isValidOn(sessionDate)
                ? balanceOrPaymentRejection(sessionDate, memberSubscriptions)
                : Optional.of(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        if (rejection.isPresent()) {
            throw new BookingNotAllowedException(rejection.get());
        }
        List<CreditUsage> updated = new ArrayList<>(usages);
        updated.add(new CreditUsage(bookingId, sessionDate));
        return withUsages(updated);
    }

    /**
     * Gives back the place held by {@code bookingId}: a free cancellation or a session
     * cancellation (RN-15). Idempotent: a booking that holds no place returns this subscription
     * unchanged, so a place can never be refunded twice. Always allowed, even after the period
     * ended or while overdue.
     */
    public Subscription refund(BookingId bookingId) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        if (!holdsPlaceFor(bookingId)) {
            return this;
        }
        return withUsages(usages.stream().filter(usage -> !usage.bookingId().equals(bookingId)).toList());
    }

    /**
     * Gives back the place held by {@code booking} if its cancellation earns a refund (RN-15,
     * {@link Booking#creditRefundable()}): a free cancellation of a confirmed booking, or a
     * session cancellation of a confirmed one. A late cancellation, a no-show, an attended or
     * still-live booking and a waitlisted booking cancelled before it held a seat return this
     * subscription unchanged. Idempotent like {@link #refund}.
     *
     * @throws IllegalArgumentException if the booking is another member's or another association's
     */
    public Subscription refundFor(Booking booking) {
        Objects.requireNonNull(booking, "booking must not be null");
        if (!booking.memberId().equals(memberId) || !booking.associationId().equals(associationId)) {
            throw new IllegalArgumentException("The booking does not belong to this subscription's member");
        }
        return booking.creditRefundable() ? refund(booking.id()) : this;
    }

    /**
     * The subscription, among a member's, that currently holds a place for {@code bookingId}:
     * where a refund must go. Empty if none does (nothing to refund).
     */
    public static Optional<Subscription> holdingPlaceFor(BookingId bookingId, Collection<Subscription> subscriptions) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(subscriptions, "subscriptions must not be null");
        return subscriptions.stream().filter(subscription -> subscription.holdsPlaceFor(bookingId)).findFirst();
    }

    /**
     * What is left for a session on {@code sessionDate}: credits left for PACK/SINGLE_SESSION,
     * sessions left in that ISO week for MONTHLY_N_PER_WEEK, empty (no limit) for MONTHLY_UNLIMITED.
     * Doesn't consider validity or payment; see {@link #rejectionFor}.
     */
    public OptionalInt balanceOn(LocalDate sessionDate) {
        return balanceOn(sessionDate, List.of());
    }

    /**
     * As {@link #balanceOn(LocalDate)}; for MONTHLY_N_PER_WEEK the week's usage of the member's
     * other weekly subscriptions in {@code memberSubscriptions} counts too, because the weekly
     * limit is per member (RN-16), e.g. when a renewal starts mid-week.
     */
    public OptionalInt balanceOn(LocalDate sessionDate, Collection<Subscription> memberSubscriptions) {
        Objects.requireNonNull(sessionDate, "sessionDate must not be null");
        Objects.requireNonNull(memberSubscriptions, "memberSubscriptions must not be null");
        return switch (terms.type()) {
            case MONTHLY_UNLIMITED -> OptionalInt.empty();
            case MONTHLY_N_PER_WEEK ->
                    OptionalInt.of(terms.sessionsPerWeek() - usedInWeekOf(sessionDate, memberSubscriptions));
            case PACK, SINGLE_SESSION -> OptionalInt.of(terms.credits() - usages.size());
        };
    }

    private int usedInWeekOf(LocalDate sessionDate, Collection<Subscription> memberSubscriptions) {
        LocalDate week = startOfWeek(sessionDate);
        long others = memberSubscriptions.stream()
                .filter(other -> !other.id.equals(id) && other.memberId.equals(memberId))
                .filter(other -> other.terms.type() == PlanType.MONTHLY_N_PER_WEEK)
                .mapToLong(other -> other.usedInWeek(week))
                .sum();
        return (int) (usedInWeek(week) + others);
    }

    private long usedInWeek(LocalDate weekStart) {
        return usages.stream().filter(usage -> startOfWeek(usage.sessionDate()).equals(weekStart)).count();
    }

    private static LocalDate startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The session's calendar date in Europe/Lisbon (architecture.md section 9). */
    private static LocalDate sessionDateOf(Instant sessionStart) {
        Objects.requireNonNull(sessionStart, "sessionStart must not be null");
        return sessionStart.atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
    }

    private Subscription withUsages(List<CreditUsage> newUsages) {
        return new Subscription(id, associationId, memberId, planId, terms, price, startDate, endDate, paymentStatus,
                List.copyOf(newUsages), version);
    }

    // ------------------------------------------------------------ period (RN-16)

    /** Whether {@code date} falls in the period, both ends inclusive. */
    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** Whether the period shares at least one day with {@code [start, end]} (both inclusive). */
    public boolean overlaps(LocalDate start, LocalDate end) {
        return !start.isAfter(endDate) && !end.isBefore(startDate);
    }

    /** The day a renewal normally starts: the day after this subscription's last day (RN-16). */
    public LocalDate renewalStartDate() {
        return endDate.plusDays(1);
    }

    /**
     * Where a renewal starts as of {@code today}: at once for an exhausted pack or single
     * session still inside its period (RN-16), otherwise the day after this one ends.
     */
    public LocalDate renewalStartDate(LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        return isExhausted() && isValidOn(today) ? today : renewalStartDate();
    }

    /**
     * A PACK or SINGLE_SESSION subscription with no credits left. It no longer counts as active
     * for the overlap check (RN-16). If a refund later gives a credit back it is active again;
     * the periods may then overlap, and eligibility charges the earliest-starting usable one.
     */
    public boolean isExhausted() {
        return terms.credits() != null && usages.size() >= terms.credits();
    }

    public boolean holdsPlaceFor(BookingId bookingId) {
        return usages.stream().anyMatch(usage -> usage.bookingId().equals(bookingId));
    }

    public SubscriptionId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public MemberId memberId() {
        return memberId;
    }

    public PlanId planId() {
        return planId;
    }

    /** The plan's rules as they were when the subscription was created. */
    public PlanTerms terms() {
        return terms;
    }

    /**
     * What the subscription costs: the plan's price when it was assigned. Like {@link #terms()}, a later edit of the
     * plan does not change it, so what a member owes (and has paid) never moves under them.
     */
    public Money price() {
        return price;
    }

    public PlanType type() {
        return terms.type();
    }

    public LocalDate startDate() {
        return startDate;
    }

    /** Last day of the period, inclusive. */
    public LocalDate endDate() {
        return endDate;
    }

    public PaymentStatus paymentStatus() {
        return paymentStatus;
    }

    /** The bookings currently holding a place, in the order they were charged. */
    public List<CreditUsage> usages() {
        return usages;
    }

    /** Optimistic-lock version: concurrent changes of the balance must not be lost or spend one credit twice. */
    public long version() {
        return version;
    }

    /** Places currently used: for a pack, credits used; for a weekly plan, across all weeks. */
    public int creditsUsed() {
        return usages.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subscription other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Subscription{id=%s, memberId=%s, status=%s}".formatted(id, memberId, paymentStatus);
    }
}
