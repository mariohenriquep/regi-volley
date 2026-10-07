package com.regivolley.api.domain.exception;

/**
 * Thrown when another member of the same association already has the email address (a person has
 * one membership per association). The same address in another association is another membership
 * and is fine. Uniqueness spans members, so only a repository can enforce it; the database
 * constraint is the backstop for two concurrent registrations.
 *
 * <p>Deliberately carries no email: it is personal data and must not reach logs or error messages.
 */
public class MemberEmailAlreadyUsedException extends BusinessRuleException {

    public MemberEmailAlreadyUsedException() {
        super("Another member of this association already uses this email address");
    }
}
