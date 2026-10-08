package com.regivolley.api.domain.exception;

/**
 * Thrown when an operation (deactivating an administrator, revoking the ADMIN role) would leave the association
 * without an active administrator, who could then never be replaced.
 */
public class LastAdministratorException extends BusinessRuleException {

    public LastAdministratorException() {
        super("The association must keep at least one active administrator");
    }
}
