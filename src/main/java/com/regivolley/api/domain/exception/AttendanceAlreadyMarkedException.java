package com.regivolley.api.domain.exception;

/** Thrown when cancelling a session that already has attendance marked (open question default: not allowed). */
public class AttendanceAlreadyMarkedException extends BusinessRuleException {

    public AttendanceAlreadyMarkedException() {
        super("A session cannot be cancelled once attendance has been marked");
    }
}
