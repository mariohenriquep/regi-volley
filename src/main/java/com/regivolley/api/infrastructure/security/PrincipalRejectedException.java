package com.regivolley.api.infrastructure.security;

import org.springframework.security.core.AuthenticationException;

import java.util.Objects;

/** A validly signed token whose owner may no longer act. The message is constant: the reason is for the log, not the client. */
public class PrincipalRejectedException extends AuthenticationException {

    private final PrincipalRejection reason;

    public PrincipalRejectedException(PrincipalRejection reason) {
        super("The token is no longer accepted");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public PrincipalRejection reason() {
        return reason;
    }
}
