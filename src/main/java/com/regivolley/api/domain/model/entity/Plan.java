package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidPlanException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.shared.AggregateRoot;

import java.time.LocalDate;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * What an association sells (RN-13, RN-14, US-19): a type, a price, how many sessions it gives
 * and for how long. Immutable and self-validating.
 *
 * <p>Validity: monthly plans last one calendar month from the start date; PACK and SINGLE_SESSION
 * plans last {@code validityDays} days (the start day counts as the first). See
 * {@link #endDateFor(LocalDate)}. A plan is created and reconstituted only by {@code PlanFactory}.
 */
public final class Plan implements AggregateRoot {

    private final PlanId id;
    private final AssociationId associationId;
    private final String name;
    private final PlanTerms terms;
    private final Money price;
    private final Integer validityDays;
    private final long version;

    /**
     * Checks every invariant, so no plan exists in an invalid state. Public because the only callers are
     * {@code PlanFactory} (new plans and persisted ones) and this class; the architecture test pins that.
     *
     * @param validityDays required (at least 1) for PACK and SINGLE_SESSION, absent for monthly plans
     * @param version      the optimistic-lock version it was loaded with (0 for a new plan); a stale copy is
     *                     detected when it is saved (architecture.md section 10)
     */
    public Plan(PlanId id, AssociationId associationId, String name, PlanTerms terms, Money price,
                Integer validityDays, long version) {
        Objects.requireNonNull(terms, "terms must not be null");
        if (version < 0) {
            throw new InvalidPlanException("The version must not be negative");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidPlanException("A plan needs a name");
        }
        boolean needsValidity = terms.type() == PlanType.PACK || terms.type() == PlanType.SINGLE_SESSION;
        if (needsValidity && (validityDays == null || validityDays < 1)) {
            throw new InvalidPlanException("A " + terms.type() + " plan needs validityDays of at least 1");
        }
        if (!needsValidity && validityDays != null) {
            throw new InvalidPlanException("A " + terms.type() + " plan has no validityDays: it lasts one month");
        }
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.associationId = Objects.requireNonNull(associationId, "associationId must not be null");
        this.name = name.trim();
        this.terms = terms;
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.validityDays = validityDays;
        this.version = version;
    }

    /**
     * Replaces everything an administrator may change (US-19), revalidating it as a whole; the id, the tenant and
     * the version stay. Subscriptions already sold keep the terms they snapshotted.
     */
    public Plan edit(String newName, PlanTerms newTerms, Money newPrice, Integer newValidityDays) {
        return new Plan(id, associationId, newName, newTerms, newPrice, newValidityDays, version);
    }

    /**
     * Last day (inclusive) of a subscription to this plan starting on {@code start}. A month runs
     * to the day before the same day-of-month next month: 01/10 ends 31/10, so a renewal starts
     * on 01/11 (RN-16). When the next month is too short for the start day (30/01, 31/01), the
     * month ends on that month's last day, so the renewal starts on the 1st of the following
     * month: 31/01 ends 28/02 and the renewal starts 01/03.
     */
    public LocalDate endDateFor(LocalDate start) {
        Objects.requireNonNull(start, "start must not be null");
        if (validityDays != null) {
            return start.plusDays(validityDays - 1L);
        }
        LocalDate sameDayNextMonth = start.plusMonths(1);
        if (start.getDayOfMonth() > sameDayNextMonth.getDayOfMonth()) {
            sameDayNextMonth = sameDayNextMonth.plusDays(1);
        }
        return sameDayNextMonth.minusDays(1);
    }

    public PlanId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public String name() {
        return name;
    }

    public PlanType type() {
        return terms.type();
    }

    /** The rule-bearing part, which a subscription snapshots. */
    public PlanTerms terms() {
        return terms;
    }

    public Money price() {
        return price;
    }

    public Set<LevelId> allowedLevels() {
        return terms.allowedLevels();
    }

    /** Optimistic-lock version: concurrent edits of the plan must not be lost. */
    public long version() {
        return version;
    }

    /** Empty for monthly plans, whose validity is one calendar month. */
    public OptionalInt validityDays() {
        return validityDays == null ? OptionalInt.empty() : OptionalInt.of(validityDays);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plan other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Plan{id=%s, type=%s}".formatted(id, terms.type());
    }
}
