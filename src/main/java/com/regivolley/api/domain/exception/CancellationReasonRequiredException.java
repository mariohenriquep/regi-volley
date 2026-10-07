package com.regivolley.api.domain.exception;

/** Thrown when a session is cancelled without a reason (RN-04, US-11). */
public class CancellationReasonRequiredException extends BusinessRuleException {

    public CancellationReasonRequiredException() {
        super("A reason is required to cancel a session");
    }
}
