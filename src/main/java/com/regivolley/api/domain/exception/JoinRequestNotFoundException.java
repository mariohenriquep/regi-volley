package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.JoinRequestId;

/** Thrown when a join request id doesn't exist in the association it is addressed to (maps to "not found"). */
public class JoinRequestNotFoundException extends RuntimeException {

    private final JoinRequestId joinRequestId;

    public JoinRequestNotFoundException(JoinRequestId joinRequestId) {
        super("Join request not found in this association: " + joinRequestId);
        this.joinRequestId = joinRequestId;
    }

    public JoinRequestId joinRequestId() {
        return joinRequestId;
    }
}
