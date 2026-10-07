package com.regivolley.api.domain.exception;

/** Thrown when revoking a role would leave a member with none: a member always holds at least one. */
public class LastRoleCannotBeRevokedException extends BusinessRuleException {

    public LastRoleCannotBeRevokedException() {
        super("The last role of a member cannot be revoked");
    }
}
