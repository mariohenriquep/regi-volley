package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.PlanId;

/** Thrown when a plan id doesn't exist in the association it is addressed to (maps to "not found"). */
public class PlanNotFoundException extends RuntimeException {

    private final PlanId planId;

    public PlanNotFoundException(PlanId planId) {
        super("Plan not found in this association: " + planId);
        this.planId = planId;
    }

    public PlanId planId() {
        return planId;
    }
}
