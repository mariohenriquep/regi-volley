package com.regivolley.api.domain.exception;

/**
 * Thrown when a visitor's request to join cannot be accepted because the email already belongs to a member of the
 * association or already has a pending request there (US-05). The message is deliberately the same for both and
 * carries no email: telling a stranger whether an address is a member would leak the association's membership.
 */
public class JoinRequestNotPossibleException extends BusinessRuleException {

    public JoinRequestNotPossibleException() {
        super("This request could not be submitted. If you already belong to this association or have a request "
                + "waiting for an answer, please contact the association");
    }
}
