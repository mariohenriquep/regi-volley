package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.MemberId;

/** Thrown when the coach given to a training group is not an active member of the association holding the COACH role (US-09). */
public class InvalidCoachException extends BusinessRuleException {

    private final MemberId coachId;

    public InvalidCoachException(MemberId coachId) {
        super("The coach must be an active member of the association holding the coach role");
        this.coachId = coachId;
    }

    public MemberId coachId() {
        return coachId;
    }
}
