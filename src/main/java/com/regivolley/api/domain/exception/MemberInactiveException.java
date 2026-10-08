package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.MemberId;

/** Thrown when something is asked of a deactivated member that only an active one can have, e.g. a new plan (US-08, US-20). */
public class MemberInactiveException extends BusinessRuleException {

    private final MemberId memberId;

    public MemberInactiveException(MemberId memberId) {
        super("The member is deactivated");
        this.memberId = memberId;
    }

    public MemberId memberId() {
        return memberId;
    }
}
