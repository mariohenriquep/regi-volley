package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidPlanException;
import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.Set;

/**
 * The part of a plan that decides whether and how often a member may book (RN-13, RN-14). A
 * {@code Subscription} keeps a copy, so editing the plan later never changes what a member
 * already bought.
 *
 * <p>Which counts apply depends on the type: {@code sessionsPerWeek} only for MONTHLY_N_PER_WEEK,
 * {@code credits} only for PACK (N) and SINGLE_SESSION (exactly 1); the other is {@code null}.
 * Use the factories rather than the canonical constructor.
 *
 * @param allowedLevels levels the plan gives access to (RN-14); empty means no restriction
 */
public record PlanTerms(PlanType type, Integer sessionsPerWeek, Integer credits, Set<LevelId> allowedLevels) implements ValueObject {

    public PlanTerms {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(allowedLevels, "allowedLevels must not be null");
        allowedLevels = Set.copyOf(allowedLevels);
        switch (type) {
            case MONTHLY_UNLIMITED -> {
                requireAbsent(sessionsPerWeek, "sessionsPerWeek", type);
                requireAbsent(credits, "credits", type);
            }
            case MONTHLY_N_PER_WEEK -> {
                requirePositive(sessionsPerWeek, "sessionsPerWeek", type);
                requireAbsent(credits, "credits", type);
            }
            case PACK -> {
                requireAbsent(sessionsPerWeek, "sessionsPerWeek", type);
                requirePositive(credits, "credits", type);
            }
            case SINGLE_SESSION -> {
                requireAbsent(sessionsPerWeek, "sessionsPerWeek", type);
                if (credits == null || credits != 1) {
                    throw new InvalidPlanException("A SINGLE_SESSION plan has exactly one credit");
                }
            }
        }
    }

    public static PlanTerms monthlyUnlimited(Set<LevelId> allowedLevels) {
        return new PlanTerms(PlanType.MONTHLY_UNLIMITED, null, null, allowedLevels);
    }

    public static PlanTerms monthlyNPerWeek(int sessionsPerWeek, Set<LevelId> allowedLevels) {
        return new PlanTerms(PlanType.MONTHLY_N_PER_WEEK, sessionsPerWeek, null, allowedLevels);
    }

    public static PlanTerms pack(int credits, Set<LevelId> allowedLevels) {
        return new PlanTerms(PlanType.PACK, null, credits, allowedLevels);
    }

    public static PlanTerms singleSession(Set<LevelId> allowedLevels) {
        return new PlanTerms(PlanType.SINGLE_SESSION, null, 1, allowedLevels);
    }

    /**
     * RN-14: whether the plan gives access to a group accepting {@code groupLevels}. An
     * unrestricted plan always does; otherwise at least one accepted level must be allowed.
     */
    public boolean allowsAnyOf(Set<LevelId> groupLevels) {
        return allowedLevels.isEmpty() || groupLevels.stream().anyMatch(allowedLevels::contains);
    }

    private static void requireAbsent(Integer value, String field, PlanType type) {
        if (value != null) {
            throw new InvalidPlanException("A " + type + " plan has no " + field);
        }
    }

    private static void requirePositive(Integer value, String field, PlanType type) {
        if (value == null || value < 1) {
            throw new InvalidPlanException("A " + type + " plan needs " + field + " of at least 1");
        }
    }
}
