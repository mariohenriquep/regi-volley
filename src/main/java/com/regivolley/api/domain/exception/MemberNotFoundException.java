package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.MemberId;

/** Thrown when a member id doesn't exist in the association it is addressed to (maps to "not found"). */
public class MemberNotFoundException extends RuntimeException {

    private final MemberId memberId;

    public MemberNotFoundException(MemberId memberId) {
        super("Member not found in this association: " + memberId);
        this.memberId = memberId;
    }

    public MemberId memberId() {
        return memberId;
    }
}
