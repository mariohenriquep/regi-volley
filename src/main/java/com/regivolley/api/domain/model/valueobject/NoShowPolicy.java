package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/**
 * Per-association no-show rule (RN-11). Decided 7/10/2026: reaching the limit only <em>warns</em> the
 * member and the administrators; nothing blocks booking. The blocking period of the original rule
 * (7 days) is left for a later phase, so it is not modelled here.
 *
 * @param monthlyLimit no-shows in one calendar month that trigger the warning (default 3)
 */
public record NoShowPolicy(int monthlyLimit) implements ValueObject {

    public static final int DEFAULT_MONTHLY_LIMIT = 3;

    public NoShowPolicy {
        if (monthlyLimit < 1) {
            throw new IllegalArgumentException("monthlyLimit must be at least 1");
        }
    }

    public static NoShowPolicy defaults() {
        return new NoShowPolicy(DEFAULT_MONTHLY_LIMIT);
    }

    /** True exactly when the member has just reached the limit, so the warning goes out once, not on every later no-show. */
    public boolean isReachedBy(int noShowsThisMonth) {
        return noShowsThisMonth == monthlyLimit;
    }

    /** True from one no-show before the limit on (US-18: "perto do bloqueio"). */
    public boolean isNear(int noShowsThisMonth) {
        return noShowsThisMonth >= monthlyLimit - 1;
    }
}
