package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.PlanId;

/**
 * A plan was changed by a concurrent request between load and save, so this write was rejected and
 * nothing was stored.
 */
public class PlanModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final PlanId planId;

    public PlanModifiedConcurrentlyException(PlanId planId) {
        super("plan", planId.value());
        this.planId = planId;
    }

    public PlanId planId() {
        return planId;
    }
}
