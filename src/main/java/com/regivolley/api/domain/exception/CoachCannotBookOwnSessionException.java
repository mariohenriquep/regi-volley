package com.regivolley.api.domain.exception;

/**
 * Thrown when the session's own coach tries to book it. The coach never takes a seat (RN-02,
 * decided 7/10/2026): they are implicitly present, so they don't book their own session.
 */
public class CoachCannotBookOwnSessionException extends BusinessRuleException {

    public CoachCannotBookOwnSessionException() {
        super("The session coach does not book a seat: they are present by default and do not count towards capacity");
    }
}
