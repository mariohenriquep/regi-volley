package com.regivolley.api.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/**
 * The authenticated state of a request: the {@link AuthenticatedActor} as principal, no authorities (roles are
 * decided by the use cases from the {@code Member}, D-5) and no credentials, so the token value is not kept anywhere.
 */
final class ActorAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedActor principal;

    ActorAuthenticationToken(AuthenticatedActor principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedActor getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
