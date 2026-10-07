package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** RN-13: the kinds of plan an association can sell. */
public enum PlanType implements ValueObject {
    /** Any number of sessions during the month. */
    MONTHLY_UNLIMITED,
    /** A month with at most N sessions per ISO week (Monday to Sunday, Europe/Lisbon). */
    MONTHLY_N_PER_WEEK,
    /** N credits usable until the pack's validity ends (e.g. 10 credits, 90 days). */
    PACK,
    /** A single credit. */
    SINGLE_SESSION
}
