package com.regivolley.api.infrastructure.notification;

/**
 * A failure that trying again cannot cure: an address that is not an address, a template that cannot be filled, a server that
 * refused the recipient for good (5xx). {@link MailDispatcher} does not retry a task that throws it and logs it once. The message
 * is written by our own code and never holds a value; the cause (when there is one) is never logged beyond its class.
 */
public final class PermanentMailFailure extends RuntimeException {

    public PermanentMailFailure(String message) {
        super(message);
    }

    public PermanentMailFailure(Throwable cause) {
        super(cause.getClass().getSimpleName(), cause);
    }

    /** What the log shows: the class of the underlying failure, or this class when there is none. */
    String reason() {
        return (getCause() == null ? this : getCause()).getClass().getSimpleName();
    }
}
